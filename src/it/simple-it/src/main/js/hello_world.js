goog.module('hello.world');

goog.require('goog.html.SafeHtml');
goog.require('greetings.world');
goog.require('proto.com.example.Name');

/** @type {proto.com.example.Name} */
var myWorld = new proto.com.example.Name();
myWorld.setText('Cle<eland');

/** @type {goog.html.SafeHtml} */
var myGreeting = greetings.world.HelloWorld(
  {
    world: myWorld
  }
);

alert(myGreeting.getContent());
