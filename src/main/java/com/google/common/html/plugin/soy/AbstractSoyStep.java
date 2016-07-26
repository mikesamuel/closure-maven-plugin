package com.google.common.html.plugin.soy;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.html.plugin.Sources.Source;
import com.google.common.html.plugin.common.Ingredients.DirScanFileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.FileIngredient;
import com.google.common.html.plugin.common.Ingredients.FileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients.PathValue;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.Step;
import com.google.common.html.plugin.plan.StepSource;
import com.google.common.io.Files;
import com.google.template.soy.SoyFileSet;

abstract class AbstractSoyStep extends Step {

  AbstractSoyStep(
      String keyPrefix,
      OptionsIngredient<SoyOptions> options,
      FileSetIngredient soySources,
      PathValue dest) {
    super(
        keyPrefix + ":[" + options.key + "];" + soySources.key + ";" + dest.key,
        ImmutableList.<Ingredient>of(options, soySources, dest),
        Sets.immutableEnumSet(StepSource.SOY_GENERATED, StepSource.SOY_SRC),
        Sets.immutableEnumSet(StepSource.JS_GENERATED));
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    OptionsIngredient<SoyOptions> optionsIng =
        ((OptionsIngredient<?>) inputs.get(0)).asSuperType(SoyOptions.class);
    DirScanFileSetIngredient soySources =
        (DirScanFileSetIngredient) inputs.get(1);
    PathValue dest = (PathValue) inputs.get(2);

    try {
      soySources.resolve(log);
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to find .soy sources", ex);
    }
    Iterable<FileIngredient> soySourceFiles = soySources.mainSources();

    if (!Iterables.isEmpty(soySourceFiles)) {
      SoyOptions options = optionsIng.getOptions();
      SoyFileSet.Builder sfsBuilder = options.toSoyFileSetBuilder(log);

      ImmutableList.Builder<Source> sources = ImmutableList.builder();
      for (FileIngredient soySource : soySourceFiles) {
        File relPath = soySource.source.relativePath;
        try {
          sfsBuilder.add(
              Files.toString(soySource.source.canonicalPath, Charsets.UTF_8),
              relPath.getPath());
        } catch (IOException ex) {
          throw new MojoExecutionException(
              "Failed to read soy source: " + relPath, ex);
        }
        sources.add(soySource.source);
      }

      compileSoy(log, options, sfsBuilder, sources.build(), dest);
    }
  }

  protected abstract void compileSoy(
      Log log, SoyOptions options, SoyFileSet.Builder sfsBuilder,
      ImmutableList<Source> sources, PathValue dest)
  throws MojoExecutionException;

  @Override
  public void skip(Log log) throws MojoExecutionException {
    // Done
  }

  @Override
  public ImmutableList<Step> extraSteps(Log log) throws MojoExecutionException {
    return ImmutableList.of();
  }

}
