def file_of = { Object... names -> new File(names.join(File.separator)) }

File logFile = new File(basedir, "build.log");

assert logFile.getText("UTF-8").contains("[INFO] BUILD SUCCESS");

File targetDir = new File(basedir, "target");
File closureOutDir = file_of(targetDir, "classes", "closure");

// Sanity check the compiled CSS
File cssOutDir = new File(closureOutDir, "css");
File compiledCssBar = new File(cssOutDir, "bar-main.css");
File compiledCssFoo = new File(cssOutDir, "foo-main.css");
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

File renameMap = new File(cssOutDir, "css-rename-map.json");
assert renameMap.exists();
assert renameMap.getText("UTF-8").trim().equals(
    ""  // Symbols from one should not clobber those from another.
    + "{\n"
    + "  \"bar\": \"a\",\n"
    + "  \"yellow\": \"b\"\n"
    + "}");


// Sanity check the sources generated from .proto files.
File proto1Java = file_of(
    targetDir, "src", "main", "java", "com", "example", "Proto1.java");
assert proto1Java.isFile();
File proto1Js = file_of(  // JS files are named after the message types.
    targetDir, "src", "main", "js", "name.js");
assert proto1Js.isFile();

// Check that the compiled JS output ends up in the right place.
File jsOutDir = new File(closureOutDir, "js");
File mainJsModule = new File(jsOutDir, "main.js");
File helloWorldJsModule = new File(jsOutDir, "hello.world.js");
assert mainJsModule.isFile();
assert helloWorldJsModule.isFile();

// Check that the generated symbols include entries for both compiled CSS & JS.
File webFilesJava = file_of(targetDir, "src", "main", "java",
                            "com", "google", "closure", "it", "WebFiles.java");
String webFilesJavaCode = webFilesJava.getText("UTF-8");
assert webFilesJavaCode.contains('String CSS_BAR_MAIN_CSS = "css/bar-main.css";');
assert webFilesJavaCode.contains('String JS_HELLO_WORLD_JS = "js/hello.world.js";');
