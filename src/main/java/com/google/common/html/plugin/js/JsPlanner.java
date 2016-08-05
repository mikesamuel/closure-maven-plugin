package com.google.common.html.plugin.js;

import java.io.File;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.html.plugin.common.CommonPlanner;
import com.google.common.html.plugin.common.Ingredients;

/**
 * A planner that invokes the closure compiler on JavaScript sources.
 */
public final class JsPlanner {
  final CommonPlanner planner;
  private File defaultJsSource;
  private File defaultJsTestSource;

  /** */
  public JsPlanner(CommonPlanner planner) {
    this.planner = planner;
  }

  /** Default source directory for production JS source files. */
  public JsPlanner defaultJsSource(File f) {
    this.defaultJsSource = f;
    return this;
  }

  /** Default source directory for test-only JS source files. */
  public JsPlanner defaultJsTestSource(File f) {
    this.defaultJsTestSource = f;
    return this;
  }

  /**
   * Adds steps to a common planner to find JS sources, extract a set of module
   * definitions, and invoke the closure compiler to build them.
   */
  public void plan(ImmutableList<JsOptions> js) {
    Preconditions.checkNotNull(defaultJsSource);
    Preconditions.checkNotNull(defaultJsTestSource);

    Ingredients ingredients = planner.ingredients;

    for (JsOptions oneJs : js) {
      planner.addStep(
          new FindJsSources(
              this,
              ingredients.options(JsOptions.class, oneJs),
              planner.genfiles,
              ingredients.pathValue(defaultJsSource),
              ingredients.pathValue(defaultJsTestSource)));
    }
  }
}
