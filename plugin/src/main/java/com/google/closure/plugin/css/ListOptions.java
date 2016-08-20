package com.google.closure.plugin.css;

import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.closure.plugin.common.DirectoryScannerSpec;
import com.google.closure.plugin.common.GenfilesDirs;
import com.google.closure.plugin.common.Ingredients;
import com.google.closure.plugin.common.Ingredients
    .DirScanFileSetIngredient;
import com.google.closure.plugin.common.Ingredients.HashedInMemory;
import com.google.closure.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.closure.plugin.plan.Ingredient;
import com.google.closure.plugin.plan.PlanKey;
import com.google.closure.plugin.plan.Step;
import com.google.closure.plugin.plan.StepSource;


final class ListOptions extends Step {
  final CssPlanner planner;
  final SerializedObjectIngredient<CssOptionsById> optionsListFile;

  ListOptions(
      CssPlanner planner,
      ImmutableList<HashedInMemory<CssOptions>> options,
      HashedInMemory<GenfilesDirs> genfiles,
      SerializedObjectIngredient<CssOptionsById> optionsListFile) {
    super(
        PlanKey.builder("list-options").build(),
        ImmutableList.<Ingredient>builder().add(genfiles).addAll(options)
            .build(),

        ImmutableSet.<StepSource>of(),
        Sets.immutableEnumSet(
            // Implicitly by the extra steps it schedules transitively.
            StepSource.CSS_COMPILED,
            StepSource.CSS_SOURCE_MAP,
            StepSource.CSS_RENAME_MAP,
            StepSource.JS_GENERATED));
    this.planner = planner;
    this.optionsListFile = optionsListFile;
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    ImmutableList.Builder<CssOptions> options = ImmutableList.builder();
    for (int i = 1, n = inputs.size(); i < n; ++i) {
      HashedInMemory<CssOptions> optionsInput =
          ((HashedInMemory<?>) inputs.get(i))
          .asSuperType(CssOptions.class);
      options.add(optionsInput.getValue());
    }

    optionsListFile.setStoredObject(new CssOptionsById(options.build()));
    try {
      optionsListFile.write();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to write options list", ex);
    }
  }

  @Override
  public void skip(Log log) throws MojoExecutionException {
    try {
      optionsListFile.read();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to read options list", ex);
    }
  }

  @Override
  public ImmutableList<Step> extraSteps(Log log)
  throws MojoExecutionException {
    HashedInMemory<GenfilesDirs> genfiles = ((HashedInMemory<?>) inputs.get(0))
       .asSuperType(GenfilesDirs.class);
    GenfilesDirs gf = genfiles.getValue();

    ImmutableList.Builder<Step> extraSteps = ImmutableList.builder();

    Ingredients ingredients = planner.planner.ingredients;
    for (CssOptions options :
         optionsListFile.getStoredObject().get().optionsById.values()) {
      DirectoryScannerSpec dsSpec = options.toDirectoryScannerSpec(
          planner.defaultCssSource(), planner.defaultCssSource(), gf);
      DirScanFileSetIngredient sources = ingredients.fileset(dsSpec);
      try {
        sources.resolve(log);
      } catch (IOException ex) {
        throw new MojoExecutionException(
            "Failed to find source files", ex);
      }

      SerializedObjectIngredient<CssBundleList> bundlesOutput;
      try {
        bundlesOutput = ingredients.serializedObject(
            "css-bundles-" + options.getId() + ".ser", CssBundleList.class);
      } catch (IOException ex) {
        throw new MojoExecutionException(
            "Failed to find place to put intermediate results", ex);
      }

      extraSteps.add(new FindEntryPoints(
          planner.planner.substitutionMapProvider,
          ingredients,
          ingredients.hashedInMemory(CssOptions.class, options),
          sources,
          ingredients.stringValue(planner.defaultCssOutputPathTemplate()),
          ingredients.stringValue(planner.defaultCssSourceMapPathTemplate()),
          bundlesOutput,
          planner.cssOutputDir()));
    }

    return extraSteps.build();
  }
}
