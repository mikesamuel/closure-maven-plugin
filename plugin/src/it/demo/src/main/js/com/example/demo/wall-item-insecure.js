/**
 * @fileoverview
 * Insecure variant of wlll-item-fixed.js.
 * See that file for more detail.
 */

goog.module('com.example.wall.item.insecure');

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
      var div = document.createElement('div');
      // Not very insecure.  They get to XSS themselves.
      div.innerHTML = html;
      return /** @type {!HTMLElement} */ (div);
    });

  /**
   * @param {proto.com.example.demo.WallItem} wallItem
   * @return {string} html
   */
  function renderWallItem(wallItem) {
    if (wallItem) {
      return (
        '<li class="' + goog.getCssName('wall-item')
          + '" style="'
          + 'left: ' + wallItem.getCentroid().getXPercent() + '%; '
          + 'top: ' + wallItem.getCentroid().getYPercent() + '%">'
          + wallItem.getHtmlUntrusted()
          + '</li>');
    } else {
      return '';
    }
  }

  registry.registerWallItemRender(
    /**
     * @param {!proto.com.example.demo.WallItem} wallItem
     * @param {!HTMLElement} follower
     */
    function (wallItem, follower) {
      // Pre-render the wall item.
      // This will be clobbered by the authoritative response from
      // wall.json.
      var newWallItemHtml = renderWallItem(wallItem);

      // http://caniuse.com/#feat=insertadjacenthtml
      follower.insertAdjacentHTML('beforebegin', newWallItemHtml);
    });

  registry.registerWallItemsRender(
    /**
     * @param {!proto.com.example.demo.WallItems} items
     * @param {!HTMLElement} container
     */
    function (items, container) {
      var newWallItemsHtml = items.getItemList().map(renderWallItem).join('');
      // http://caniuse.com/#feat=insertadjacenthtml
      container.insertAdjacentHTML('afterbegin', newWallItemsHtml);
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
