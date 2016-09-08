goog.module('com.example.wall.item.overescaping');

// We rerender the Soy template on the client when we receive an update.
goog.require('com.example.demo');
goog.require('goog.soy');

goog.require('proto.com.example.demo.Point');
goog.require('proto.com.example.demo.WallItem');
goog.require('proto.com.example.demo.WallItems');

(function () {
  var registry = goog.require('com.example.wall');

  registry.registerDomElementFromHtml(
    /**
     * @param {!string} html
     * @return {!HTMLElement}
     */
    function domElementFromHtml(html) {
      // Render the wall-item into an <li> and then move the cotnent
      // int a <div>.
      var newWallItemElement = goog.soy.renderAsElement(
        com.example.demo.WallItem,
        { item: makeWallItem(html, {x: 50, y:50}) });

      var div = document.createElement('div');
      while (newWallItemElement.firstChild) {
        div.appendChild(newWallItemElement.firstChild);
      }
      return /** @type {!HTMLElement} */ (div);
    });

  registry.registerWallItemRender(
    /**
     * @param {!proto.com.example.demo.WallItem} wallItem
     * @param {!HTMLElement} follower
     */
    function (wallItem, follower) {
      // Pre-render the wall item.
      // This will be clobbered by the authoritative response from
      // wall.json.
      var newWallItemHtml = com.example.demo.WallItem({ item: wallItem })
            .getContent();

      // http://caniuse.com/#feat=insertadjacenthtml
      follower.insertAdjacentHTML('beforebegin', newWallItemHtml);
    });

  registry.registerWallItemsRender(
    /**
     * @param {!proto.com.example.demo.WallItems} items
     * @param {!HTMLElement} container
     */
    function (items, container) {
      var newWallItemsHtml = com.example.demo.WallItems({ wall: items })
          .getContent();

      container.insertAdjacentHTML('afterbegin', newWallItemsHtml);
    });

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
  }

  registry.registerMakeWallItem(makeWallItem);
}());
