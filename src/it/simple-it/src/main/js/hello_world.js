goog.module('hello.world');

goog.require('proto.com.example.Name');
goog.require('greetings.world');

/** @type {proto.com.example.Name} */
var myWorld = new proto.com.example.Name();
myWorld.setText('Cle<eland');

var myGreeting = greetings.world.HelloWorld(
  {
    world: myWorld
  }
);

alert(myGreeting);
