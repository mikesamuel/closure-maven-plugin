package com.google.common.html.plugin.css;

import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.css.SubstitutionMapProvider;
import com.google.common.html.plugin.common.Ingredients.FileIngredient;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients.SerializedObjectIngredient;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.Step;

final class CompileOneBundle extends Step {

  private final SubstitutionMapProvider substMap;

  CompileOneBundle(
      SubstitutionMapProvider substMap,
      OptionsIngredient<CssOptions> options,
      SerializedObjectIngredient<CssBundle> bundle,
      ImmutableList<FileIngredient> inputFiles,
      FileIngredient cssFile,
      FileIngredient sourceMapFile,
      FileIngredient renameMapFile) {
    super(
        "compile-css:" + cssFile.source.canonicalPath,
        ImmutableList.<Ingredient>builder().add(options).add(bundle)
            .addAll(inputFiles).build(),
        ImmutableList.<Ingredient>of(cssFile, sourceMapFile, renameMapFile));
    this.substMap = substMap;
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    CssOptions cssOptions =
        ((OptionsIngredient<?>) inputs.get(0))
        .asSuperType(CssOptions.class)
        .getOptions();

    CssBundle bundle =
        ((SerializedObjectIngredient<?>) inputs.get(1))
        .asSuperType(CssBundle.class)
        .getStoredObject().get();

    Preconditions.checkState(bundle.optionsId.equals(cssOptions.getId()));

    FileIngredient cssFile = (FileIngredient) outputs.get(0);
    FileIngredient sourceMapFile = (FileIngredient) outputs.get(1);
    FileIngredient renameMapFile = (FileIngredient) outputs.get(2);

    boolean ok;
    try {
      ok = new CssCompilerWrapper()
          .cssOptions(cssOptions)
          .inputs(bundle.inputs)
          .outputFile(cssFile.source.canonicalPath)
          .sourceMapFile(sourceMapFile.source.canonicalPath)
          .substitutionMapProvider(substMap)
          .compileCss(log);
    } catch (IOException ex) {
      log.error(ex);
      ok = false;
    }
    if (!ok) {
      throw new MojoExecutionException(
          "Failed to compile CSS " + bundle.entryPoint.relativePath);
    }
    if (log.isDebugEnabled()) {
      log.debug("After " + renameMapFile.source.canonicalPath);
    }
  }

  @Override
  public void skip(Log log) throws MojoExecutionException {
    // Nothing to do.
  }

  @Override
  public ImmutableList<Step> extraSteps(Log log) {
    return ImmutableList.of();
  }

}
