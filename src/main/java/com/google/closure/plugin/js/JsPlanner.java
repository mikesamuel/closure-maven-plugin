package com.google.common.html.plugin.js;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.html.plugin.common.CommonPlanner;
import com.google.common.html.plugin.common.Ingredients;
import com.google.common.html.plugin.common.Ingredients.SerializedObjectIngredient;
import com.google.common.html.plugin.common.OptionsUtils;

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
  public void plan(ImmutableList<JsOptions> unpreparedJs)
  throws IOException, MojoExecutionException {
    Preconditions.checkNotNull(defaultJsSource);
    Preconditions.checkNotNull(defaultJsTestSource);

    ImmutableList<JsOptions> js = OptionsUtils.prepare(
        new Supplier<JsOptions>() {
          @Override
          public JsOptions get() {
            return new JsOptions();
          }
        },
        unpreparedJs);

    Ingredients ingredients = planner.ingredients;

    for (JsOptions oneJs : js) {
      SerializedObjectIngredient<JsDepInfo> depInfoIng =
          ingredients.serializedObject(
              new File(
                  new File(planner.outputDir, "js"),
                  "dep-info-" + oneJs.getId() + ".ser"),
              JsDepInfo.class);

      SerializedObjectIngredient<Modules> modulesIng =
          ingredients.serializedObject(
              new File(
                  new File(planner.outputDir, "js"),
                  "modules-" + oneJs.getId() + ".ser"),
              Modules.class);


      planner.addStep(
          new FindJsSources(
              this,
              ingredients.hashedInMemory(JsOptions.class, oneJs),
              planner.genfiles, depInfoIng, modulesIng,
              ingredients.pathValue(defaultJsSource),
              ingredients.pathValue(defaultJsTestSource)));
    }
  }
}
