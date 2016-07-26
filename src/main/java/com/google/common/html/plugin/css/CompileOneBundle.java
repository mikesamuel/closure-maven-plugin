package com.google.common.html.plugin.css;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.css.SubstitutionMapProvider;
import com.google.common.html.plugin.common.Ingredients.FileIngredient;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients.SerializedObjectIngredient;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.PlanKey;
import com.google.common.html.plugin.plan.Step;
import com.google.common.html.plugin.plan.StepSource;

final class CompileOneBundle extends Step {

  private final SubstitutionMapProvider substMap;

  CompileOneBundle(
      File outPath,
      SubstitutionMapProvider substMap,
      OptionsIngredient<CssOptions> options,
      SerializedObjectIngredient<CssBundle> bundle,
      ImmutableList<FileIngredient> inputFiles) {
    super(
        PlanKey.builder("compile-css").addString(outPath.getPath()).build(),
        ImmutableList.<Ingredient>builder().add(options).add(bundle)
            .addAll(inputFiles).build(),
            Sets.immutableEnumSet(
                StepSource.CSS_SRC, StepSource.CSS_GENERATED),
            Sets.immutableEnumSet(
                StepSource.CSS_COMPILED,
                StepSource.CSS_SOURCE_MAP,
                StepSource.CSS_RENAME_MAP,
                // Since the CSS_RENAME_MAP is an input to the JSCompiler.
                StepSource.JS_GENERATED));
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

    File cssFile = bundle.outputs.css;
    File sourceMapFile = bundle.outputs.sourceMap;

    boolean ok;
    try {
      ok = new CssCompilerWrapper()
          .cssOptions(cssOptions)
          .inputs(bundle.inputs)
          .outputFile(cssFile)
          .sourceMapFile(sourceMapFile)
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
