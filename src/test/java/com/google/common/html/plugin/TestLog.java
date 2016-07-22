package com.google.common.html.plugin;

import org.apache.maven.plugin.logging.Log;

/** */
public final class TestLog implements Log {

  @Override
  public void debug(CharSequence msg) {
    // Do nothing
  }

  @Override
  public void debug(Throwable err) {
    // Do nothing
  }

  @Override
  public void debug(CharSequence msg, Throwable err) {
    // Do nothing
  }

  @Override
  public void error(CharSequence msg) {
    // Do nothing
  }

  @Override
  public void error(Throwable err) {
    // Do nothing
  }

  @Override
  public void error(CharSequence msg, Throwable err) {
    // Do nothing
  }

  @Override
  public void info(CharSequence msg) {
    // Do nothing
  }

  @Override
  public void info(Throwable err) {
    // Do nothing
  }

  @Override
  public void info(CharSequence msg, Throwable err) {
    // Do nothing
  }

  @Override
  public boolean isDebugEnabled() {
    return false;
  }

  @Override
  public boolean isErrorEnabled() {
    return false;
  }

  @Override
  public boolean isInfoEnabled() {
    return false;
  }

  @Override
  public boolean isWarnEnabled() {
    return false;
  }

  @Override
  public void warn(CharSequence msg) {
    // Do nothing
  }

  @Override
  public void warn(Throwable err) {
    // Do nothing
  }

  @Override
  public void warn(CharSequence msg, Throwable err) {
    // Do nothing
  }

}
