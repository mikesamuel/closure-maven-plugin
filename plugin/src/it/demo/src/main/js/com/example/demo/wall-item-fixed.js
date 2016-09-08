/**
 * @fileoverview
 * Plugs into wall.js to do things in an XSS-safe way
 * unlike the other variants which show a variety of
 * problems due to failure to adequately handle untrusted
 * inputs or cosmetic problems due to over-compensating.
 */

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
   * Any XSS is not persisted since the server-side
   * sanitizer is authoritative.
   *
   * @return {!goog.html.sanitizer.HtmlSanitizer}
   */
  function newSanitizer() {
    return new goog.html.sanitizer.HtmlSanitizer.Builder()
      .allowCssStyles()
      .build();
  }

  /**
   * Called to render the contents of the chip that
   * displays content before a wall item is sent to
   * the server.
   */
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
     * Pre-renders a wall item that is being sent to the server
     * so that there is no visible display between an update
     * being sent and the authoritative content arriving back.
     *
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
     * Re-render all wall items.  Used when an update is received
     * from the server, either due to a ping that does not return
     * 304(Not Modified) or as the body of a Wall POST result.
     *
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
     * Programatically create a Wall Item.
     *
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
