/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @fileoverview
 * Value converters for protocol buffers in Soy that are semantically
 * similar to Soy builtin types.
 *
 * <p>Calls to these are generated by
 * com.google.template.soy.types.proto.SoyProtoTypeImpl.
 *
 * @author Mike Samuel
 */


goog.provide('soydata.unpackProtoToSanitizedCss');
goog.provide('soydata.unpackProtoToSanitizedHtml');
goog.provide('soydata.unpackProtoToSanitizedJs');
goog.provide('soydata.unpackProtoToSanitizedTrustedResourceUri');
goog.provide('soydata.unpackProtoToSanitizedUri');

goog.require('goog.html.SafeHtml');
goog.require('goog.html.SafeScript');
goog.require('goog.html.SafeStyle');
goog.require('goog.html.SafeStyleSheet');
goog.require('goog.html.SafeUrl');
goog.require('goog.html.TrustedResourceUrl');
goog.require('proto.webutil.html.types.SafeHtmlProto');
goog.require('proto.webutil.html.types.SafeScriptProto');
goog.require('proto.webutil.html.types.SafeStyleProto');
goog.require('proto.webutil.html.types.SafeStyleSheetProto');
goog.require('proto.webutil.html.types.SafeUrlProto');
goog.require('proto.webutil.html.types.TrustedResourceUrlProto');
goog.require('security.html.jspbconversions');
goog.require('soydata.VERY_UNSAFE');

/**
 * Converts a Safe String Proto to HTML Sanitized Content.
 * @param {?proto.webutil.html.types.SafeHtmlProto} x null or a safe string proto.
 * @return {?soydata.SanitizedHtml}
 */
soydata.unpackProtoToSanitizedHtml = function(x) {
  if (x instanceof proto.webutil.html.types.SafeHtmlProto) {
    var safeHtml = security.html.jspbconversions.safeHtmlFromProto(x);
    return soydata.VERY_UNSAFE.ordainSanitizedHtml(
        goog.html.SafeHtml.unwrap(safeHtml), safeHtml.getDirection());
  }
  return null;
};


/**
 * Converts a Safe String Proto to CSS Sanitized Content.
 * @param {?proto.webutil.html.types.SafeStyleProto | proto.webutil.html.types.SafeStyleSheetProto} x
 *   null or a safe string proto.
 * @return {?soydata.SanitizedCss}
 */
soydata.unpackProtoToSanitizedCss = function(x) {
  var safeCss;
  if (x instanceof proto.webutil.html.types.SafeStyleProto) {
    safeCss = security.html.jspbconversions.safeStyleFromProto(x);
    return soydata.VERY_UNSAFE.ordainSanitizedCss(
        goog.html.SafeStyle.unwrap(safeCss));
  } else if (x instanceof proto.webutil.html.types.SafeStyleSheetProto) {
    safeCss = security.html.jspbconversions.safeStyleSheetFromProto(x);
    return soydata.VERY_UNSAFE.ordainSanitizedCss(
        goog.html.SafeStyleSheet.unwrap(safeCss));
  }
  return null;
};


/**
 * Converts a Safe String Proto to JS Sanitized Content.
 * @param {?proto.webutil.html.types.SafeScriptProto} x null or a safe string proto.
 * @return {?soydata.SanitizedJs}
 */
soydata.unpackProtoToSanitizedJs = function(x) {
  if (x instanceof proto.webutil.html.types.SafeScriptProto) {
    var safeJs = security.html.jspbconversions.safeScriptFromProto(x);
    return soydata.VERY_UNSAFE.ordainSanitizedJs(
        goog.html.SafeScript.unwrap(safeJs));
  }
  return null;
};


/**
 * Converts a Safe String Proto to URI Sanitized Content.
 * @param {?proto.webutil.html.types.SafeUrlProto | proto.webutil.html.types.TrustedResourceUrlProto} x
 *   null or a safe string proto.
 * @return {?soydata.SanitizedUri}
 */
soydata.unpackProtoToSanitizedUri = function(x) {
  var safeUrl;
  if (x instanceof proto.webutil.html.types.SafeUrlProto) {
    safeUrl = security.html.jspbconversions.safeUrlFromProto(x);
    return soydata.VERY_UNSAFE.ordainSanitizedUri(
        goog.html.SafeUrl.unwrap(safeUrl));
  }
  return null;
};


/**
 * Converts a Safe String Proto to a Trusted Resource URI Sanitized Content.
 * @param {?proto.webutil.html.types.TrustedResourceUrlProto} x
 *   null or a safe string proto.
 * @return {?soydata.SanitizedTrustedResourceUri}
 */
soydata.unpackProtoToSanitizedTrustedResourceUri = function(x) {
  var safeUrl;
  if (x instanceof proto.webutil.html.types.TrustedResourceUrlProto) {
    safeUrl = security.html.jspbconversions.trustedResourceUrlFromProto(x);
    return soydata.VERY_UNSAFE.ordainSanitizedTrustedResourceUri(
        goog.html.TrustedResourceUrl.unwrap(safeUrl));
  }
  return null;
};
