package com.google.common.html.plugin.css;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.collect.ImmutableList;
import com.google.common.html.plugin.Sources;
import com.google.common.html.plugin.common.Ingredients;
import com.google.common.html.plugin.common.Ingredients.FileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients.SerializedObjectIngredient;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.Step;


final class ListOptions extends Step {
  final CssPlanner planner;

  ListOptions(
      CssPlanner planner,
      ImmutableList<OptionsIngredient<CssOptions>> options,
      SerializedObjectIngredient<CssOptionsById> optionsListFile) {
    super(
        "explode-options",
        ImmutableList.<Ingredient>copyOf(options),
        ImmutableList.<Ingredient>of(optionsListFile));
    this.planner = planner;
  }

  SerializedObjectIngredient<CssOptionsById> getOptionsListFile() {
    return ((SerializedObjectIngredient<?>) outputs.get(0)).asSuperType(
          CssOptionsById.class);
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {

    CssOptions[] options = new CssOptions[inputs.size()];
    for (int i = 0; i < options.length; ++i) {
      OptionsIngredient<CssOptions> optionsInput =
          ((OptionsIngredient<?>) inputs.get(i)).asSuperType(CssOptions.class);
      options[i] = optionsInput.getOptions();
    }

    ImmutableList<CssOptions> exploded = CssOptions.asplode(options);

    SerializedObjectIngredient<CssOptionsById> optionsListFile =
        getOptionsListFile();
    optionsListFile.setStoredObject(new CssOptionsById(exploded));
    try {
      optionsListFile.write();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to write options list", ex);
    }
  }

  @Override
  public void skip(Log log) throws MojoExecutionException {
    SerializedObjectIngredient<CssOptionsById> optionsListFile =
        getOptionsListFile();
    try {
      optionsListFile.read();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to read options list", ex);
    }
  }

  @Override
  public ImmutableList<Step> extraSteps(Log log)
  throws MojoExecutionException {
    ImmutableList.Builder<Step> extraSteps = ImmutableList.builder();

    SerializedObjectIngredient<CssOptionsById> optionsListFile =
        getOptionsListFile();
    Ingredients ingredients = planner.planner.ingredients;
    for (CssOptions options :
         optionsListFile.getStoredObject().get().optionsById.values()) {
      File bundlesFile = new File(
          planner.cssOutputDir(), "css-bundles-" + options.getId());
      ImmutableList<File> roots;
      if (options.source == null || options.source.length == 0) {
        roots = ImmutableList.of(planner.defaultCssSource());
      } else {
        roots = ImmutableList.copyOf(options.source);
      }
      FileSetIngredient sources = ingredients.fileset(
          new Sources.Finder(".css", ".gss").mainRoots(roots));
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
          ingredients.options(CssOptions.class, options),
          sources,
          ingredients.stringValue(planner.defaultCssOutputPathTemplate()),
          ingredients.stringValue(planner.defaultCssSourceMapPathTemplate()),
          ingredients.stringValue(planner.cssRenameMap().getPath()),
          bundlesOutput));
    }

    return extraSteps.build();
  }
}
