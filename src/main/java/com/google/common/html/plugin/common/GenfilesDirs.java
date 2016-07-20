package com.google.common.html.plugin.common;

import java.io.File;
import java.io.Serializable;

import com.google.common.base.Preconditions;

/**
 * The directories where code-generators should put their output.
 */
public final class GenfilesDirs implements Serializable {

  private static final long serialVersionUID = 1635729944473350335L;

  /** The {@code target} directory. */
  public final File outputDir;

  /** Output directory for production java sources. */
  public final File javaGenfiles;
  /** Output directory for test-only java sources. */
  public final File javaTestGenfiles;
  /** Output directory for production javascript sources. */
  public final File jsGenfiles;
  /** Output directory for test-only javascript sources. */
  public final File jsTestGenfiles;

  /** */
  public GenfilesDirs(
      File outputDir,
      File javaGenfiles, File javaTestGenfiles,
      File jsGenfiles, File jsTestGenfiles) {
    this.outputDir = outputDir;
    this.javaGenfiles = javaGenfiles;
    this.javaTestGenfiles = javaTestGenfiles;
    this.jsGenfiles = jsGenfiles;
    this.jsTestGenfiles = jsTestGenfiles;
  }

  /**
   * The generated sources directory for files with the given extension.
   */
  public File getGeneratedSourceDirectoryForExtension(
      String extension, boolean isTestScope) {
    Preconditions.checkArgument(!extension.contains("."), extension);
    String canonExtension = "gss".equals(extension) ? "css" : extension;
    File base;
    if ("js".equals(canonExtension)) {
      base = isTestScope ? jsTestGenfiles : jsGenfiles;
    } else if ("java".equals(canonExtension)) {  // Should not be reached
      base = isTestScope ? javaTestGenfiles : javaGenfiles;
    } else {
      // suffix of "css" -> "target/src/main/css"
      base = new File(
          new File(
              new File(outputDir, "src"),
              (isTestScope ? "test" : "main")),
          canonExtension);
    }
    return base;
  }
}
