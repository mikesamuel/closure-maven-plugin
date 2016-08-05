package com.google.common.html.plugin.js;

import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.html.plugin.Sources;
import com.google.common.html.plugin.common.GenfilesDirs;
import com.google.common.html.plugin.common.Ingredients.DirScanFileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients.PathValue;
import com.google.common.html.plugin.common.Ingredients.SerializedObjectIngredient;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.PlanKey;
import com.google.common.html.plugin.plan.Step;
import com.google.common.html.plugin.plan.StepSource;

final class FindJsSources extends Step {
  private final JsPlanner planner;

  protected FindJsSources(
      JsPlanner planner, OptionsIngredient<JsOptions> options,
      SerializedObjectIngredient<GenfilesDirs> genfilesHolder,
      PathValue defaultJsSource, PathValue defaultJsTestSource) {
    super(
        PlanKey.builder("find-js")
            .addInp(
                options, genfilesHolder, defaultJsSource, defaultJsTestSource)
            .build(),
        ImmutableList.<Ingredient>of(
            options, genfilesHolder, defaultJsSource, defaultJsTestSource),
        Sets.immutableEnumSet(StepSource.JS_GENERATED, StepSource.JS_SRC),
        ImmutableSet.<StepSource>of());
    this.planner = planner;
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    // Work done in extraSteps
  }

  @Override
  public void skip(Log log) throws MojoExecutionException {
    // Work done in extraSteps
  }

  @Override
  public ImmutableList<Step> extraSteps(Log log) throws MojoExecutionException {
    OptionsIngredient<JsOptions> optionsIng =
        ((OptionsIngredient<?>) inputs.get(0)).asSuperType(JsOptions.class);
    SerializedObjectIngredient<GenfilesDirs> genfilesHolder =
        ((SerializedObjectIngredient<?>) inputs.get(1))
        .asSuperType(GenfilesDirs.class);
    PathValue defaultJsSource = (PathValue) inputs.get(2);
    PathValue defaultJsTestSource = (PathValue) inputs.get(3);

    JsOptions options = optionsIng.getOptions();
    GenfilesDirs genfiles = genfilesHolder.getStoredObject().get();

    Sources.Finder finder = new Sources.Finder(".js", ".ts");
    if (options.source != null && options.source.length != 0) {
      finder.mainRoots(options.source);
    } else {
      finder.mainRoots(defaultJsSource.value);
    }
    finder.mainRoots(genfiles.jsGenfiles);
    if (options.testSource != null && options.testSource.length != 0) {
      finder.testRoots(options.testSource);
    } else {
      finder.testRoots(defaultJsTestSource.value);
    }
    finder.testRoots(genfiles.jsTestGenfiles);

    DirScanFileSetIngredient fs = planner.planner.ingredients.fileset(finder);
    try {
      fs.resolve(log);
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to find JS sources", ex);
    }

    return ImmutableList.<Step>of(
        new ComputeJsDepGraph(planner, optionsIng, fs));
  }
}
