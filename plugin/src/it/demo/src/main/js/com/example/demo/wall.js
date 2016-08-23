goog.module('com.example.wall');

goog.require('goog.events');
goog.require('goog.events.EventType');
goog.require('goog.fx.DragDrop');
goog.require('goog.style');

goog.events.listen(
  document,
  goog.events.EventType.DOMCONTENTLOADED,
  function () {
    var scribbleForm = document.querySelector('form#scribble');
    var submitButton = scribbleForm.querySelector('button#scribble-submit');
    var htmlTextarea = scribbleForm.querySelector('textarea#html');
    var xPosition = scribbleForm.querySelector('input#x');
    var yPosition = scribbleForm.querySelector('input#y');

    /**
     * A wall-item under development that has not been committed to the
     * backend.
     *
     * @param {boolean} opt_forceVisible
     * @return {HTMLElement}
     */
    function getEditorChip(opt_forceVisible) {
      var chip = document.getElementById('chip');
      if (opt_forceVisible) {
        // The chip is invisible by default.
        goog.style.setStyle(chip, 'display', 'inline-block');
      }
      return chip;
    }

    goog.events.listen(
      htmlTextarea, goog.events.EventType.CHANGE,
      rateLimit(function () {
        goog.dom.setInnerHtml(
          getEditorChip(),
          htmlTextarea.value);
      }));

    goog.events.listen(
      xPosition, goog.events.EventType.CHANGE,
      rateLimit(function () {
        var pct = Math.max(0, Math.min(100, +xPosition.value));
        if (pct === pct) {
          goog.style.setStyle(getEditorChip(), 'left', pct + '%');
        }
      }));

    goog.events.listen(
      yPosition, goog.events.EventType.CHANGE,
      rateLimit(function () {
        var pct = Math.max(0, Math.min(100, +yPosition.value));
        if (pct === pct) {
          goog.style.setStyle(getEditorChip(), 'top', pct + '%');
        }
      }));

    goog.events.listen(
      submitButton, goog.events.EventType.CLICK,
      function () {
        console.log('clicked');
      });


    /**
     * A function that, when called, causes f to be called
     * within roughly opt_rate_ms, but all other calls within
     * that window will be folded into one call to f.
     *
     * @param {!function ():void} f
     * @param {number} opt_rate_ms
     * @return {!function ():void}
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
  });
