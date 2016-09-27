package com.google.closure.plugin.common;

import java.io.File;
import java.util.EnumSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * The directories where code-generators should put their output.
 */
public final class GenfilesDirs {

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
    this.outputDir = Preconditions.checkNotNull(outputDir);
    this.javaGenfiles = Preconditions.checkNotNull(javaGenfiles);
    this.javaTestGenfiles = Preconditions.checkNotNull(javaTestGenfiles);
    this.jsGenfiles = Preconditions.checkNotNull(jsGenfiles);
    this.jsTestGenfiles = Preconditions.checkNotNull(jsTestGenfiles);
  }

  /**
   * The generated sources directory for files with the given extension and
   * properties.
   */
  public File getGeneratedSourceDirectory(
      FileExt extension, SourceFileProperty... props) {
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
      FileExt extension, Iterable<SourceFileProperty> props) {
    ImmutableSet<SourceFileProperty> propSet =
        Sets.immutableEnumSet(props);

    boolean isTestScope = propSet.contains(SourceFileProperty.TEST_ONLY);
    boolean isDep = propSet.contains(SourceFileProperty.LOAD_AS_NEEDED);

    File base;
    if (FileExt.JS.equals(extension) && !isDep) {
      base = isTestScope ? jsTestGenfiles : jsGenfiles;
    } else if (FileExt.JAVA.equals(extension) && !isDep) {
      base = isTestScope ? javaTestGenfiles : javaGenfiles;
    } else {
      // suffix of "css" w/o props -> "target/src/main/css"
      base = new File(
          new File(
              new File(outputDir, isDep ? "dep" : "src"),
              (isTestScope ? "test" : "main")),
          extension.extension);
    }
    return base;
  }
}
