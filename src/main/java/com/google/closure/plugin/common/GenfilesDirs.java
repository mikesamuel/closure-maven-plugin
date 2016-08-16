package com.google.closure.plugin.common;

import java.io.File;
import java.io.Serializable;
import java.util.EnumSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

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

  private static final ImmutableMap<String, String> CANON_EXTENSION =
      ImmutableMap.of(
          "gss", "css",  // Google Stylesheets can live under css
          "ts",  "js"  // Typed script can live under js
          );

  /**
   * The generated sources directory for files with the given extension and
   * properties.
   */
  public File getGeneratedSourceDirectory(
      String extension, SourceFileProperty... props) {
    EnumSet<SourceFileProperty> propSet =
        EnumSet.noneOf(SourceFileProperty.class);
    for (SourceFileProperty p : props) {
      propSet.add(p);
    }
    return getGeneratedSourceDirectory(extension, propSet);
  }

    /**
     * The generated sources directory for files with the given extension
     * and properties.
     */
    public File getGeneratedSourceDirectory(
        String extension, Iterable<SourceFileProperty> props) {
      ImmutableSet<SourceFileProperty> propSet =
          Sets.immutableEnumSet(props);

      Preconditions.checkArgument(!extension.contains("."), extension);
      String canonExtension = CANON_EXTENSION.get(extension);
      if (canonExtension == null) { canonExtension = extension; }

      boolean isTestScope = propSet.contains(SourceFileProperty.TEST_ONLY);
      boolean isDep = propSet.contains(SourceFileProperty.LOAD_AS_NEEDED);

      File base;
      if ("js".equals(canonExtension) && !isDep) {
      base = isTestScope ? jsTestGenfiles : jsGenfiles;
    } else if ("java".equals(canonExtension) && !isDep) {
      base = isTestScope ? javaTestGenfiles : javaGenfiles;
    } else {
      // suffix of "css" w/o props -> "target/src/main/css"
      base = new File(
          new File(
              new File(outputDir, isDep ? "dep" : "src"),
              (isTestScope ? "test" : "main")),
          canonExtension);
    }
    return base;
  }
}
