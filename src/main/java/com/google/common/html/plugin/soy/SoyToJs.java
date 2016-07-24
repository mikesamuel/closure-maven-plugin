package com.google.common.html.plugin.soy;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
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
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.msgs.SoyMsgBundle;

final class SoyToJs extends Step {

  SoyToJs(
      OptionsIngredient<SoyOptions> options,
      FileSetIngredient soySources,
      PathValue jsOutDir) {
    super(
        "soy-to-js:[" + options.key + "];" + soySources.key,
        ImmutableList.<Ingredient>of(options, soySources, jsOutDir),
        Sets.immutableEnumSet(StepSource.SOY_GENERATED, StepSource.SOY_SRC),
        Sets.immutableEnumSet(StepSource.JS_GENERATED));
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    OptionsIngredient<SoyOptions> optionsIng =
        ((OptionsIngredient<?>) inputs.get(0)).asSuperType(SoyOptions.class);
    DirScanFileSetIngredient soySources =
        (DirScanFileSetIngredient) inputs.get(1);
    PathValue jsOutDir = (PathValue) inputs.get(2);

    try {
      soySources.resolve(log);
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to find .soy sources", ex);
    }
    Iterable<FileIngredient> soySourceFiles = soySources.mainSources().get();

    if (!Iterables.isEmpty(soySourceFiles)) {
      SoyOptions options = optionsIng.getOptions();

      SoyFileSet.Builder sfsBuilder = options.toSoyFileSetBuilder(log);

      ImmutableList.Builder<File> relPaths = ImmutableList.builder();
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
        relPaths.add(relPath);
      }

      SoyFileSet sfs = sfsBuilder.build();

      ImmutableList<Js> allJsSrc;
      if (options.js != null && options.js.length != 0) {
        allJsSrc = ImmutableList.copyOf(options.js);
      } else {
        Js js = new Js();
        allJsSrc = ImmutableList.of(js);
      }

      for (Js js : allJsSrc) {
        SoyJsSrcOptions jsSrcOptions = js.toSoyJsSrcOptions(log);
        SoyMsgBundle msgBundle = null;

        List<String> jsFileContent = sfs.compileToJsSrc(jsSrcOptions, msgBundle);
        File outputDir = jsOutDir.value;
        outputDir.mkdirs();

        ImmutableList<File> inputRelPaths = relPaths.build();
        int nOutputs = jsFileContent.size();
        Preconditions.checkState(nOutputs == inputRelPaths.size());
        for (int i = 0; i < nOutputs; ++i) {
          File inputRelPath = inputRelPaths.get(i);

          // Disambiguate with js.id if not null (constructed above).
          String suffix = ".js";
          if (js.id != null && !"".equals(js.id)) {
            suffix = "_" + js.id + suffix;
          }

          String compiledJsContent = jsFileContent.get(i);
          File outputRelPath = new File(
            inputRelPath.getParentFile(),
            FilenameUtils.getBaseName(inputRelPath.getName()) + suffix);
          File outputPath = new File(FilenameUtils.concat(
              outputDir.getPath(), outputRelPath.getPath()));
          try {
            Files.write(compiledJsContent, outputPath, Charsets.UTF_8);
          } catch (IOException ex) {
            throw new MojoExecutionException(
                "Failed to write soy templates compiled from " + inputRelPath
                + " to " + outputPath,
                ex);
          }
        }
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
