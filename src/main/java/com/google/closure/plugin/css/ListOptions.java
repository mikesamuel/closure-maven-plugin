package com.google.common.html.plugin.css;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.html.plugin.common.DirectoryScannerSpec;
import com.google.common.html.plugin.common.GenfilesDirs;
import com.google.common.html.plugin.common.Ingredients;
import com.google.common.html.plugin.common.Ingredients
    .DirScanFileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.HashedInMemory;
import com.google.common.html.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.PlanKey;
import com.google.common.html.plugin.plan.Step;
import com.google.common.html.plugin.plan.StepSource;


final class ListOptions extends Step {
  final CssPlanner planner;
  final SerializedObjectIngredient<CssOptionsById> optionsListFile;

  ListOptions(
      CssPlanner planner,
      ImmutableList<HashedInMemory<CssOptions>> options,
      SerializedObjectIngredient<GenfilesDirs> genfiles,
      SerializedObjectIngredient<CssOptionsById> optionsListFile) {
    super(
        PlanKey.builder("list-options").build(),
        ImmutableList.<Ingredient>builder().add(genfiles).addAll(options)
            .build(),

        ImmutableSet.<StepSource>of(),
        ImmutableSet.<StepSource>of());
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
    SerializedObjectIngredient<GenfilesDirs> genfiles =
        ((SerializedObjectIngredient<?>) inputs.get(0))
       .asSuperType(GenfilesDirs.class);
    GenfilesDirs gf = genfiles.getStoredObject().get();

    ImmutableList.Builder<Step> extraSteps = ImmutableList.builder();

    Ingredients ingredients = planner.planner.ingredients;
    for (CssOptions options :
         optionsListFile.getStoredObject().get().optionsById.values()) {
      File bundlesFile = new File(
          planner.cssOutputDir(), "css-bundles-" + options.getId() + ".ser");
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
            bundlesFile, CssBundleList.class);
      } catch (IOException ex) {
        throw new MojoExecutionException(
            "Failed to find place to put intermediate results", ex);
      }

      extraSteps.add(new FindEntryPoints(
          planner.planner.substitutionMapProvider,
          ingredients,
          planner.cssOutputDir(),
          ingredients.hashedInMemory(CssOptions.class, options),
          sources,
          ingredients.stringValue(planner.defaultCssOutputPathTemplate()),
          ingredients.stringValue(planner.defaultCssSourceMapPathTemplate()),
          bundlesOutput));
    }

    return extraSteps.build();
  }
}
