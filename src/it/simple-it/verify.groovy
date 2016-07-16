File logFile = new File(basedir, "build.log");

assert logFile.getText("UTF-8").contains("[INFO] BUILD SUCCESS");

File targetDir = new File(basedir, "target");
File targetCssDir = new File(targetDir, "css");

File compiledCssBar = new File(targetCssDir, "compiled-bar-main.css");
File compiledCssFoo = new File(targetCssDir, "compiled-foo-main.css");
assert compiledCssBar.exists();
assert compiledCssFoo.exists();

File renameMap = new File(targetCssDir, "css-rename-map.json");
assert renameMap.exists();
assert renameMap.getText("UTF-8").trim().equals(
    ""  // Symbols from one should not clobber those from another.
    + "{\n"
    + "  \"bar\": \"a\",\n"
    + "  \"yellow\": \"b\"\n"
    + "}");
