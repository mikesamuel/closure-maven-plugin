package com.google.common.html.plugin.common;

import java.io.File;
import java.io.Serializable;

/**
 * The directories where code-generators should put their output.
 */
public final class GenfilesDirs implements Serializable {

  private static final long serialVersionUID = 1635729944473350335L;

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
      File javaGenfiles, File javaTestGenfiles,
      File jsGenfiles, File jsTestGenfiles) {
    this.javaGenfiles = javaGenfiles;
    this.javaTestGenfiles = javaTestGenfiles;
    this.jsGenfiles = jsGenfiles;
    this.jsTestGenfiles = jsTestGenfiles;
  }
}
