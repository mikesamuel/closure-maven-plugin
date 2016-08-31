/**
 * @fileoverview
 * JSPB fromObject is not available in open-source version.
 * Inline it.
 */

goog.provide('com.example.unpack');

goog.require('goog.html.uncheckedconversions');
goog.require('goog.string.Const');
goog.require('proto.com.example.demo.Point');
goog.require('proto.com.example.demo.Update');
goog.require('proto.com.example.demo.WallItem');
goog.require('proto.com.example.demo.WallItems');
goog.require('proto.webutil.html.types.SafeHtmlProto');
goog.require('security.html.jspbconversions');

/**
 * @param {!Object} JSON serialization of an update proto.
 * @return {!proto.com.example.demo.Update}
 */
com.example.unpack.unpackUpdate = function (o) {
  var result = new proto.com.example.demo.Update();
  for (var k in o) {
    if (!Object.hasOwnProperty.call(o, k)) { continue; }
    var v = o[k];
    switch (k) {
    case 'items':
      result.setItems(com.example.unpack.unpackWallItems(v));
      break;
    case 'version':
      result.setVersion(v);
      break;
    default:
      throw new Error(k);
    }
  }
  return result;
};

/**
 * @param {!Object} JSON serialization of a wall items proto.
 * @return {!proto.com.example.demo.WallItems}
 * @private
 */
com.example.unpack.unpackWallItems = function (o) {
  var result = new proto.com.example.demo.WallItems();
  for (var k in o) {
    if (!Object.hasOwnProperty.call(o, k)) { continue; }
    var v = o[k];
    switch (k) {
    case 'item':
      result.setItemList(
        v.map(com.example.unpack.unpackWallItem));
      break;
    default:
      throw new Error(k);
    }
  }
  return result;
};

/**
 * @param {!Object} JSON serialization of a wall item proto.
 * @return {!proto.com.example.demo.WallItem}
 * @private
 */
com.example.unpack.unpackWallItem = function (o) {
  var result = new proto.com.example.demo.WallItem();
  for (var k in o) {
    if (!Object.hasOwnProperty.call(o, k)) { continue; }
    var v = o[k];
    switch (k) {
    case 'html':
      result.setHtml(com.example.unpack.unpackSafeHtml(v));
      break;
    case 'htmlUntrusted':
      result.setHtmlUntrusted(v);
      break;
    case 'centroid':
      result.setCentroid(com.example.unpack.unpackPoint(v));
      break;
    default:
      throw new Error(k);
    }
  }
  return result;
};

/** @type {goog.string.Const} */
com.example.unpack.UNPACK_SAFE_HTML_JUSTIFICATION =
  goog.string.Const.from('contract was checked on the server');

/**
 * @param {!Object} JSON serialization of a safe HTML proto.
 * @return {!proto.webutil.html.types.SafeHtmlProto}
 * @private
 */
com.example.unpack.unpackSafeHtml = function (o) {
  var safeHtml = goog.html.uncheckedconversions.safeHtmlFromStringKnownToSatisfyTypeContract(
    com.example.unpack.UNPACK_SAFE_HTML_JUSTIFICATION,
    o['privateDoNotAccessOrElseSafeHtmlWrappedValue']);
  return security.html.jspbconversions.safeHtmlToProto(safeHtml);
};

/**
 * @param {!Object} JSON serialization of a point proto.
 * @return {!proto.com.example.demo.Point}
 * @private
 */
com.example.unpack.unpackPoint = function (o) {
  var result = new proto.com.example.demo.Point();
  for (var k in o) {
    if (!Object.hasOwnProperty.call(o, k)) { continue; }
    var v = o[k];
    switch (k) {
    case 'xPercent':
      result.setXPercent(v);
      break;
    case 'yPercent':
      result.setYPercent(v);
      break;
    }
  }
  return result;
};
