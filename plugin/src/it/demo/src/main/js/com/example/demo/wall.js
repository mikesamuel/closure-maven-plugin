goog.module('com.example.wall');

// These abstract away some browser weirdness.
goog.require('goog.dom.classlist');
goog.require('goog.dom.safe');
goog.require('goog.events');
goog.require('goog.events.EventType');
goog.require('goog.net.XhrIo');
goog.require('goog.style');

// A library for drag-and-drop so the fragment we're editing
// can be dragged to a new location.
goog.require('goog.fx.Dragger');
// We sanitize HTML client side for quick display to the user
// even though that result is not trusted by the server.
goog.require('goog.html.sanitizer.HtmlSanitizer');
// Bridges Soy templates and SafeHTML
goog.require('goog.soy.Renderer');

// We rerender the Soy template on the client when we receive an update.
goog.require('com.example.demo');

// We manipulate protobufs programatically.
goog.require('proto.com.example.demo.Point');
goog.require('proto.com.example.demo.Update');
goog.require('proto.com.example.demo.WallItem');
goog.require('proto.com.example.demo.WallItems');
goog.require('proto.webutil.html.types.SafeHtmlProto');
goog.require('security.html.jspbconversions');


goog.events.listen(
  document,
  goog.events.EventType.DOMCONTENTLOADED,
  function () {
    var scribbleForm = document.querySelector('form#scribble');
    var submitButton = scribbleForm.querySelector('button#scribble-submit');
    var htmlTextarea = scribbleForm.querySelector('textarea#html');
    var xPosition = scribbleForm.querySelector('input#x');
    var yPosition = scribbleForm.querySelector('input#y');
    var chip = document.getElementById('chip');
    var version = +document.body.getAttribute('data-wall-version');

    // Check that we can at least find stuff in the DOM.
    goog.asserts.assert(!!scribbleForm);
    goog.asserts.assert(!!submitButton);
    goog.asserts.assert(!!htmlTextarea);
    goog.asserts.assert(!!xPosition);
    goog.asserts.assert(!!yPosition);
    goog.asserts.assert(!!chip);
    goog.asserts.assert(version === (version | 0));

    // Hook into the DOM for the events we need.
    var chipTextListener = rateLimit(updateChipContent);
    var chipPositionListener = rateLimit(updateChipPosition);
    [
      goog.events.EventType.CHANGE,
      goog.events.EventType.KEYUP,
      goog.events.EventType.CUT,
      goog.events.EventType.PASTE
    ].forEach(
      function (eventType) {
        goog.events.listen(htmlTextarea, eventType, chipTextListener);
      });

    [
      goog.events.EventType.CHANGE,
      goog.events.EventType.INPUT
    ].forEach(
      function (eventType) {
        goog.events.listen(xPosition, eventType, chipPositionListener);
        goog.events.listen(yPosition, eventType, chipPositionListener);
      });

    goog.events.listen(
        submitButton, goog.events.EventType.CLICK, addItemToWall);

    var chipDragger = new goog.fx.Dragger(chip, chip);
    goog.events.listen(chipDragger, 'start',
                       function () {
                         goog.style.setOpacity(this.target, 0.5);
                       });
    goog.events.listen(chipDragger, 'end',
                       function () {
                         goog.style.setOpacity(this.target, 1.0);
                         var x = (chip.offsetLeft + chip.offsetWidth / 2) /
                               chip.offsetParent.offsetWidth;
                         var y = (chip.offsetTop + chip.offsetHeight / 2) /
                               chip.offsetParent.offsetHeight;
                         var xPerMille = ((x * 1000) | 0);
                         var yPerMille = ((y * 1000) | 0);
                         setChipPosition({
                           x: xPerMille / 10,
                           y: yPerMille / 10
                         });
                       });

    // Initialize chip editor state to that currently in the editor.
    updateChipContent();
    updateChipPosition();

    // Periodically ping the server for changes.
    setInterval(
      function () {
        // We send the current version so that the server can just respond
        // with
        goog.net.XhrIo.send(
          'wall.json?have=' + encodeURIComponent('' + version),
          maybeUpdateWallFromJson,
          'GET');

      },
      5000 /* ms */);

    /**
     * Simulate the sanitization done on the server.
     * Any XSS is not persisted.
     *
     * @return {goog.html.sanitizer.HtmlSanitizer}
     */
    function newSanitizer() {
      return new goog.html.sanitizer.HtmlSanitizer.Builder()
        .allowCssStyles()
        .build();
    }

    /**
     * Update the HTML displayed so that the preview tracks the
     * editor form state.
     */
    function updateChipContent() {
      var htmlValue = htmlTextarea.value;
      // The chip is invisible by default.
      var className = goog.getCssName('blank');
      if (/\S/.test(htmlValue)) {
        goog.dom.classlist.remove(chip, className);
      } else {
        goog.dom.classlist.add(chip, className);
      }

      //goog.dom.safe.setInnerHtml(chip, htmlValue);
      var safeDomElement = newSanitizer().sanitizeToDomNode(htmlValue);
      goog.dom.removeChildren(chip);
      goog.dom.append(chip, safeDomElement);
    }

    /**
     * @param {number} n
     * @param {number} left
     * @param {number} right
     * @return {number} NaN or an n' such that left <= n' <= right.
     */
    function bound(left, right, n) {
      return Math.max(left, Math.min(right, +n));
    }

    /**
     * @return {{x:number,y:number}}
     */
    function getChipPosition() {
      return {
        x: bound(0, 100, +xPosition.value),
        // y slider is upside down.
        y: 100 - bound(0, 100, +yPosition.value)
      };
    }

    /** @param {{x:number,y:number}} p */
    function setChipPosition(p) {
      var x = p.x;
      var y = p.y;
      goog.asserts.assert('number' === typeof x);
      goog.asserts.assert('number' === typeof y);
      x = bound(0, 100, x);
      y = 100 - bound(0, 100, y);
      xPosition.value = x.toFixed(1);
      yPosition.value = y.toFixed(1);
    }

    function updateChipPosition() {
      var pos = getChipPosition();
      var x = pos.x;
      var y = pos.y;
      if (x === x) {
        goog.style.setStyle(chip, 'left', x + '%');
      }
      if (y === y) {
        goog.style.setStyle(chip, 'top', y + '%');
      }
    }

    /**
     * @return {proto.com.example.demo.WallItem}
     */
    function getWallItemFromChip() {
      var location = new proto.com.example.demo.Point();
      var position = getChipPosition();
      location.setXPercent(position.x | 0);
      location.setYPercent(position.y | 0);

      var wallItem = new proto.com.example.demo.WallItem();
      wallItem.setHtmlUntrusted(htmlTextarea.value);
      wallItem.setCentroid(location);
      return wallItem;
    }

    /**
     * Turns the editor state into a wall item, adds it locally, and
     * commits it to the server.
     */
    function addItemToWall() {
      console.log('add wall item');
      // Create a wall item.
      var wallItem = getWallItemFromChip();

      goog.net.XhrIo.send(
        'wall.json',
        updateWallFromJson,
        'POST',
        JSON.stringify(wallItem.toObject()));

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
        chip, goog.dom.safe.InsertAdjacentHtmlPosition.BEFOREBEGIN,
        newWallItemHtml);

      // TODO: reset properly.
      scribbleForm.reset();
    }

    /**
     * Handles Update protos received in JSON format.
     * The JSPB binary wire format has not been standardized enough to
     * protobuf team's liking for them to support it publically.
     *
     * @param {*=} _ ignored.
     * @this {goog.net.XhrIo}
     */
    function updateWallFromJson(_) {
      var xhr = this;

      // If we got a redirect to a different nonce, then we've been
      // orphaned by a server restart, or a cache invalidation.
      // Ignore the new version number.
      if (!/json/.test(xhr.getResponseHeader('Content-type'))) {
        console.log('orphaned');
        return;
      }

      var update = unpackUpdate(xhr.getResponseJson());
      var updateVersion = update.getVersion();

      if (updateVersion > version) {
        console.log('update wall %d -> %d', version, updateVersion);
        version = updateVersion;
        var items = update.getItems();

        // Remove everything but the chip
        var wallContainer = chip.parentNode;
        for (var firstChild; (firstChild = wallContainer.firstChild);) {
          if (firstChild === chip) {
            break;
          }
          wallContainer.removeChild(firstChild);
        }

        // Re-render the template to produce updated items.
        /** @type {goog.html.SafeHtml} */
        var newWallItemsHtml = (new goog.soy.Renderer).renderSafeHtml(
          com.example.demo.WallItems, { wall: items });
        goog.dom.safe.insertAdjacentHtml(
          wallContainer, goog.dom.safe.InsertAdjacentHtmlPosition.AFTERBEGIN,
          newWallItemsHtml);
      } else {
        console.log(
          'Discarding update.  Have %d, but received update with version %d',
          version, updateVersion);
      }
    }

    /**
     * Handles Update protos received in JSON format.
     * The JSPB binary wire format has not been standardized enough to
     * protobuf team's liking for them to support it publically.
     *
     * @this {goog.net.XhrIo}
     */
    function maybeUpdateWallFromJson() {
      var xhr = this;
      switch (xhr.getStatus()) {
      case 200:  // OK
        updateWallFromJson.call(xhr);
        break;
      case 304:  // Not modified
        console.log('Not modified');
        break;
      }
    }

    /**
     * A function that, when called, causes f to be called
     * within roughly opt_rate_ms, but all other calls within
     * that window will be folded into one call to f.
     *
     * Used to avoid slowing down the UI when there are frequent
     * interactions with an input element.
     *
     * @param {!function ():void} f a side-effecting function.
     * @param {number=} opt_rate_ms default is 100ms.
     * @return {!function ():void} a function that will cause f but less
     *    frequently than 1/rate (modulo setTimeout scheduler hiccoughs).
     */
    function rateLimit(f, opt_rate_ms) {
      var rate_ms = opt_rate_ms || 100 /* ms */;
      /** {@type number|null} */
      var timeoutId = null;

      if (!(rate_ms > 0)) {
        throw new Error(rate_ms);
      }

      return function () {
        if (timeoutId === null) {
          timeoutId = setTimeout(
            function () {
              timeoutId = null;
              f();
            },
            rate_ms);
        }
      };
    }

    // TODO Move the unpack code to a separate file.

    // JSPB fromObject is not available in open-source version.
    // Inline it.
    function unpackUpdate(o) {
      var result = new proto.com.example.demo.Update();
      for (var k in o) {
        if (!Object.hasOwnProperty.call(o, k)) { continue; }
        var v = o[k];
        switch (k) {
        case 'items':
          result.setItems(unpackWallItems(v));
          break;
        case 'version':
          result.setVersion(v);
          break;
        default:
          throw new Error(k);
        }
      }
      return result;
    }
    function unpackWallItems(o) {
      var result = new proto.com.example.demo.WallItems();
      for (var k in o) {
        if (!Object.hasOwnProperty.call(o, k)) { continue; }
        var v = o[k];
        switch (k) {
        case 'item':
          result.setItemList(
            v.map(unpackWallItem));
          break;
        default:
          throw new Error(k);
        }
      }
      return result;
    }
    function unpackWallItem(o) {
      var result = new proto.com.example.demo.WallItem();
      for (var k in o) {
        if (!Object.hasOwnProperty.call(o, k)) { continue; }
        var v = o[k];
        switch (k) {
        case 'html':
          result.setHtml(unpackSafeHtml(v));
          break;
        case 'htmlUntrusted':
          result.setHtmlUntrusted(v);
          break;
        case 'centroid':
          result.setCentroid(unpackPoint(v));
          break;
        default:
          throw new Error(k);
        }
      }
      return result;
    }
    function unpackSafeHtml(o) {
      var result = new proto.webutil.html.types.SafeHtmlProto();
      result.setPrivateDoNotAccessOrElseSafeHtmlWrappedValue(
        o['privateDoNotAccessOrElseSafeHtmlWrappedValue']);
      return result;
    }
    function unpackPoint(o) {
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
    }
  });
