package com.google.closure.plugin.js;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.closure.plugin.common.CommonPlanner;
import com.google.closure.plugin.common.DirectoryScannerSpec;
import com.google.closure.plugin.common.GenfilesDirs;
import com.google.closure.plugin.common.Ingredients;
import com.google.closure.plugin.common.Ingredients.DirScanFileSetIngredient;
import com.google.closure.plugin.common.Ingredients.HashedInMemory;
import com.google.closure.plugin.common.Ingredients.PathValue;
import com.google.closure.plugin.common.Ingredients.SerializedObjectIngredient;
import com.google.closure.plugin.common.OptionsUtils;

/**
 * A planner that invokes the closure compiler on JavaScript sources.
 */
public final class JsPlanner {
  final CommonPlanner planner;
  private PathValue defaultJsSource;
  private PathValue defaultJsTestSource;

  /** */
  public JsPlanner(CommonPlanner planner) {
    this.planner = planner;
  }

  /** Default source directory for production JS source files. */
  public JsPlanner defaultJsSource(File f) {
    this.defaultJsSource = planner.ingredients.pathValue(f);
    return this;
  }

  /** Default source directory for test-only JS source files. */
  public JsPlanner defaultJsTestSource(File f) {
    this.defaultJsTestSource = planner.ingredients.pathValue(f);
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
              "dep-info-" + oneJs.getId() + ".ser",
              JsDepInfo.class);

      SerializedObjectIngredient<Modules> modulesIng =
          ingredients.serializedObject(
              "modules-" + oneJs.getId() + ".ser",
              Modules.class);

      HashedInMemory<JsOptions> optionsIng =
          ingredients.hashedInMemory(JsOptions.class, oneJs);
      HashedInMemory<GenfilesDirs> genfilesHolder = planner.genfiles;

      GenfilesDirs genfiles = genfilesHolder.getValue();

      DirectoryScannerSpec sourcesSpec = oneJs.toDirectoryScannerSpec(
          defaultJsSource.value, defaultJsTestSource.value, genfiles);

      DirScanFileSetIngredient fs = ingredients.fileset(sourcesSpec);

      planner.addStep(new ComputeJsDepInfo(optionsIng, depInfoIng, fs));
      planner.addStep(new ComputeJsDepGraph(
          this, optionsIng, depInfoIng, modulesIng, fs));
    }
  }
}
