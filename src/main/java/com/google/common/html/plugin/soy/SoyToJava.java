package com.google.common.html.plugin.soy;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.html.plugin.Cheats;
import com.google.common.html.plugin.common.Ingredients.DirScanFileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.FileIngredient;
import com.google.common.html.plugin.common.Ingredients.FileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients.PathValue;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.Step;
import com.google.common.html.plugin.plan.StepSource;
import com.google.common.io.ByteSink;
import com.google.common.io.Files;
import com.google.template.soy.SoyFileSet;

final class SoyToJava extends Step {

  SoyToJava(
      OptionsIngredient<SoyOptions> options,
      FileSetIngredient soySources,
      PathValue outputJar) {
    super(
        "soy-to-js:[" + options.key + "];" + soySources.key,
        ImmutableList.<Ingredient>of(options, soySources, outputJar),
        Sets.immutableEnumSet(StepSource.SOY_GENERATED, StepSource.SOY_SRC),
        ImmutableSet.<StepSource>of());
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    OptionsIngredient<SoyOptions> options =
        ((OptionsIngredient<?>) inputs.get(0)).asSuperType(SoyOptions.class);
    DirScanFileSetIngredient soySources =
        (DirScanFileSetIngredient) inputs.get(1);
    PathValue outputJarPath = (PathValue) inputs.get(2);

    try {
      soySources.resolve(log);
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to find .soy sources", ex);
    }
    Iterable<FileIngredient> soySourceFiles = soySources.mainSources();

    if (!Iterables.isEmpty(soySourceFiles)) {
      SoyFileSet.Builder sfsBuilder =
        options.getOptions().toSoyFileSetBuilder(log);

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
      }

      ByteSink classJarOut = Files.asByteSink(outputJarPath.value);
      Optional<ByteSink> srcJarOut = Optional.of(Files.asByteSink(
          new File(
              outputJarPath.value.getParentFile(),
              FilenameUtils.removeExtension(
                  outputJarPath.value.getName()) + "-src.jar")));

      try {
        Cheats.cheatCall(
            Void.class, SoyFileSet.class, sfsBuilder.build(), "compileToJar",
            ByteSink.class, classJarOut,
            Optional.class, srcJarOut);
        // TODO: expose compileToJar publicly.
      } catch (InvocationTargetException ex) {
        throw new MojoExecutionException(
            "Compilation of templates to a Java JAR failed",
            /* An IOException */ ex.getTargetException());
      }
    }
  }

  @Override
  public void skip(Log log) throws MojoExecutionException {
    // Done
  }

  @Override
  public ImmutableList<Step> extraSteps(Log log) throws MojoExecutionException {
    return ImmutableList.of();
  }

}
