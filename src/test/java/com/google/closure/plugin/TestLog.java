package com.google.closure.plugin;

import org.apache.maven.plugin.logging.Log;

/** A log object useful for testing. */
public final class TestLog implements Log {

  private boolean verbose;

  /** True if log trace is dumped to stderr, false when dropped silently. */
  public boolean verbose() {
    return verbose;
  }

  /**
   * @param b true if log trace should be dumped to stderr, false when it
   *     should be dropped silently.
   */
  public TestLog verbose(boolean b) {
    this.verbose = b;
    return this;
  }

  private void log(String prefix, CharSequence msg, Throwable err) {
    if (verbose) {
      System.err.println(msg != null ? prefix + ": " + msg : prefix);
      if (err != null) { err.printStackTrace(); }
    }
  }

  @Override
  public void debug(CharSequence msg) {
    debug(msg, null);
  }

  @Override
  public void debug(Throwable err) {
    debug(null, err);
  }

  @Override
  public void debug(CharSequence msg, Throwable err) {
    log("DEBUG", msg, err);
  }

  @Override
  public void error(CharSequence msg) {
    error(msg, null);
  }

  @Override
  public void error(Throwable err) {
    error(null, err);
  }

  @Override
  public void error(CharSequence msg, Throwable err) {
    log("ERROR", msg, err);
  }

  @Override
  public void info(CharSequence msg) {
    info(msg, null);
  }

  @Override
  public void info(Throwable err) {
    info(null, err);
  }

  @Override
  public void info(CharSequence msg, Throwable err) {
    log("INFO", msg, err);
  }

  @Override
  public boolean isDebugEnabled() {
    return verbose;
  }

  @Override
  public boolean isErrorEnabled() {
    return verbose;
  }

  @Override
  public boolean isInfoEnabled() {
    return verbose;
  }

  @Override
  public boolean isWarnEnabled() {
    return verbose;
  }

  @Override
  public void warn(CharSequence msg) {
    warn(msg, null);
  }

  @Override
  public void warn(Throwable err) {
    warn(null, err);
  }

  @Override
  public void warn(CharSequence msg, Throwable err) {
    log("WARN", msg, err);
  }

}
