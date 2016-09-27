package com.google.closure.plugin.js;

import java.util.List;

import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.ErrorManager;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.LightweightMessageFormatter;
import com.google.javascript.jscomp.MessageFormatter;
import com.google.javascript.jscomp.SourceExcerptProvider;

final class MavenLogJSErrorManager implements ErrorManager {
  private final Log log;
  private final List<JSError> errors = Lists.newArrayList();
  private final List<JSError> warnings = Lists.newArrayList();
  private double typedPercent = 0D;
  private SourceExcerptProvider sourceExcerptProvider = null;

  private static final JSError[] ZERO_ERRORS = new JSError[0];

  MavenLogJSErrorManager(Log log) {
    this.log = log;
  }

  @Override
  public void generateReport() {
    MessageFormatter formatter = new LightweightMessageFormatter(
        Preconditions.checkNotNull(sourceExcerptProvider));
    for (JSError error : errors) {
      log.error(error.format(CheckLevel.ERROR, formatter));
    }
    for (JSError warning : warnings) {
      log.warn(warning.format(CheckLevel.WARNING, formatter));
    }
  }

  @Override
  public int getErrorCount() {
    return errors.size();
  }

  @Override
  public JSError[] getErrors() {
    return errors.toArray(ZERO_ERRORS);
  }

  @Override
  public double getTypedPercent() {
    return typedPercent;
  }

  @Override
  public int getWarningCount() {
    return warnings.size();
  }

  @Override
  public JSError[] getWarnings() {
    return warnings.toArray(ZERO_ERRORS);
  }

  @Override
  public void report(CheckLevel lvl, JSError err) {
    (lvl == CheckLevel.WARNING ? warnings : errors).add(err);
  }

  @Override
  public void setTypedPercent(double newTypedPercent) {
    this.typedPercent = newTypedPercent;
  }
}