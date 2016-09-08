goog.module('com.example.wall.item.fixed');

// We rerender the Soy template on the client when we receive an update.
goog.require('com.example.demo');

goog.require('goog.dom.safe');
goog.require('goog.html.SafeHtml');
// We sanitize HTML client side for quick display to the user
// even though that result is not trusted by the server.
goog.require('goog.html.sanitizer.HtmlSanitizer');
// Bridges Soy templates and SafeHTML
goog.require('goog.soy.Renderer');

goog.require('proto.com.example.demo.Point');
goog.require('proto.com.example.demo.WallItem');
goog.require('proto.com.example.demo.WallItems');

goog.require('security.html.jspbconversions');

(function () {
  var registry = goog.require('com.example.wall');

  /**
   * Simulate the sanitization done on the server.
   * Any XSS is not persisted.
   *
   * @return {!goog.html.sanitizer.HtmlSanitizer}
   */
  function newSanitizer() {
    return new goog.html.sanitizer.HtmlSanitizer.Builder()
      .allowCssStyles()
      .build();
  }

  registry.registerDomElementFromHtml(
    /**
     * @param {!string} html
     * @return {!HTMLElement}
     */
    function domElementFromHtml(html) {
      return /** @type {!HTMLElement} */ (
        newSanitizer().sanitizeToDomNode(html)
      );
    });

  registry.registerWallItemRender(
    /**
     * @param {!proto.com.example.demo.WallItem} wallItem
     * @param {!HTMLElement} follower
     */
    function (wallItem, follower) {
      // Attach the safeHtml by sanitizing client side.
      wallItem.setHtml(
        security.html.jspbconversions.safeHtmlToProto(
          goog.html.sanitizer.HtmlSanitizer.sanitize(
            wallItem.getHtmlUntrusted())));

      // Pre-render the wall item.
      // This will be clobbered by the authoritative response from
      // wall.json.
      var newWallItemHtml = (new goog.soy.Renderer).renderSafeHtml(
        com.example.demo.WallItem, { item: wallItem });

      goog.dom.safe.insertAdjacentHtml(
        follower, goog.dom.safe.InsertAdjacentHtmlPosition.BEFOREBEGIN,
        newWallItemHtml);
    });

  registry.registerWallItemsRender(
    /**
     * @param {!proto.com.example.demo.WallItems} items
     * @param {!HTMLElement} container
     */
    function (items, container) {
      /** @type {goog.html.SafeHtml} */
      var newWallItemsHtml = (new goog.soy.Renderer).renderSafeHtml(
          com.example.demo.WallItems, { wall: items });
      goog.dom.safe.insertAdjacentHtml(
          container, goog.dom.safe.InsertAdjacentHtmlPosition.AFTERBEGIN,
          newWallItemsHtml);
    });

  registry.registerMakeWallItem(
    /**
     * @param {string} html
     * @param {!{x:number,y:number}} centroid
     * @return {!proto.com.example.demo.WallItem}
     */
    function makeWallItem(html, centroid) {
      var location = new proto.com.example.demo.Point();
      location.setXPercent(centroid.x | 0);
      location.setYPercent(centroid.y | 0);

      var wallItem = new proto.com.example.demo.WallItem();
      wallItem.setHtmlUntrusted(html);
      wallItem.setCentroid(location);

      return wallItem;
    });
}());
