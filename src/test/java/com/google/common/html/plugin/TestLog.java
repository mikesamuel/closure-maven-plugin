package com.google.common.html.plugin;

import org.apache.maven.plugin.logging.Log;

/** */
public final class TestLog implements Log {

  public void debug(CharSequence msg) {
    // Do nothing
  }

  public void debug(Throwable err) {
 // Do nothing
  }

  public void debug(CharSequence msg, Throwable err) {
    // Do nothing
  }

  public void error(CharSequence msg) {
    // Do nothing
  }

  public void error(Throwable err) {
    // Do nothing
  }

  public void error(CharSequence msg, Throwable err) {
    // Do nothing
  }

  public void info(CharSequence msg) {
    // Do nothing
  }

  public void info(Throwable err) {
    // Do nothing
  }

  public void info(CharSequence msg, Throwable err) {
    // Do nothing
  }

  public boolean isDebugEnabled() {
    return false;
  }

  public boolean isErrorEnabled() {
    return false;
  }

  public boolean isInfoEnabled() {
    return false;
  }

  public boolean isWarnEnabled() {
    return false;
  }

  public void warn(CharSequence msg) {
    // Do nothing
  }

  public void warn(Throwable err) {
    // Do nothing
  }

  public void warn(CharSequence msg, Throwable err) {
    // Do nothing
  }

}
