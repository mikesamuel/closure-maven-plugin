File logFile = new File(basedir, "build.log");

assert logFile.getText("UTF-8").contains("[INFO] BUILD SUCCESS");

File targetDir = new File(basedir, "target");

// Sanity check the compiled CSS
File targetCssDir = new File(targetDir, "css");
File compiledCssBar = new File(targetCssDir, "compiled-bar-main.css");
File compiledCssFoo = new File(targetCssDir, "compiled-foo-main.css");
assert compiledCssBar.exists();
assert compiledCssFoo.exists();
// Make sure the content from common/styles.css is present and the @import is
// not.
String barCss = compiledCssBar.getText("UTF-8");
assert barCss.contains("background-color");
assert !barCss.contains("@import");

String fooCss = compiledCssBar.getText("UTF-8");
assert fooCss.contains("background-color");
assert !fooCss.contains("@import");

File renameMap = new File(targetCssDir, "css-rename-map.json");
assert renameMap.exists();
assert renameMap.getText("UTF-8").trim().equals(
    ""  // Symbols from one should not clobber those from another.
    + "{\n"
    + "  \"bar\": \"a\",\n"
    + "  \"yellow\": \"b\"\n"
    + "}");


// Sanity check the sources generated from .proto files.
File proto1Java = new File(
    [targetDir, "src", "main", "java", "com", "example",
     "Proto1.java"].join(File.separator));
assert proto1Java.isFile();
File proto1Js = new File(  // JS files are named after the message types.
    [targetDir, "src", "main", "js", "name.js"].join(File.separator));
assert proto1Js.isFile();

// Check that the compiled JS output ends up in the right place.
File jsOutputDir = new File(new File(targetDir, "classes"), "js");
File mainJsModule = new File(jsOutputDir, "main.js");
File helloWorldJsModule = new File(jsOutputDir, "hello.world.js");
assert mainJsModule.isFile();
assert helloWorldJsModule.isFile();
