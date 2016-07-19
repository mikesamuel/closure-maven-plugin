File logFile = new File(basedir, "build.log");

assert logFile.getText("UTF-8").contains("[INFO] BUILD SUCCESS");

File targetDir = new File(basedir, "target");
File targetCssDir = new File(targetDir, "css");

File compiledCssBar = new File(targetCssDir, "compiled-bar-main.css");
File compiledCssFoo = new File(targetCssDir, "compiled-foo-main.css");
assert compiledCssBar.exists();
assert compiledCssFoo.exists();
// Make sure the content from common/styles.css is present and the @import is not.
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
