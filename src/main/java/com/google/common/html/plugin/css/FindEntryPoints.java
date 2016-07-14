package com.google.common.html.plugin.css;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.css.SubstitutionMapProvider;
import com.google.common.html.plugin.OutputAmbiguityChecker;
import com.google.common.html.plugin.Sources;
import com.google.common.html.plugin.Sources.Source;
import com.google.common.html.plugin.common.Ingredients;
import com.google.common.html.plugin.common.Ingredients.FileIngredient;
import com.google.common.html.plugin.common.Ingredients.FileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients.SerializedObjectIngredient;
import com.google.common.html.plugin.common.Ingredients.StringValue;
import com.google.common.html.plugin.css.CssImportGraph.Dependencies;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.Step;

final class FindEntryPoints extends Step {
  final SubstitutionMapProvider substMap;
  final Ingredients ingredients;
  final File cssOutputDirectory;

  FindEntryPoints(
      SubstitutionMapProvider substMap,
      Ingredients ingredients,
      File cssOutputDirectory,
      Ingredients.OptionsIngredient<CssOptions> options,
      FileSetIngredient cssSources,
      StringValue defaultCssOutputPathTemplate,
      StringValue defaultCssSourceMapTemplate,
      StringValue renameMapFilePath,
      SerializedObjectIngredient<CssBundleList> entryPointsFile) {
    super(
        "css-find-entry-points:[" + options.getId() + "]:" + cssSources.key,
        ImmutableList.<Ingredient>of(
            options,
            cssSources,
            defaultCssOutputPathTemplate,
            defaultCssSourceMapTemplate,
            renameMapFilePath),
        ImmutableList.<Ingredient>of(entryPointsFile));
    this.substMap = substMap;
    this.ingredients = ingredients;
    this.cssOutputDirectory = cssOutputDirectory;
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    ImmutableList.Builder<CssBundle> b = ImmutableList.builder();

    Preconditions.checkState(inputs.size() == 5);
    CssOptions cssOpts = ((OptionsIngredient<?>) inputs.get(0))
        .asSuperType(CssOptions.class)
        .getOptions();
    FileSetIngredient cssSources = (FileSetIngredient) inputs.get(1);
    StringValue defaultCssOutputPathTemplate = (StringValue) inputs.get(2);
    StringValue defaultCssSourceMapPathTemplate = (StringValue) inputs.get(3);

    CssImportGraph importGraph;
    try {
      cssSources.resolve(log);

      ImmutableList.Builder<Source> mainSources = ImmutableList.builder();
      Optional<ImmutableList<FileIngredient>> files =
          cssSources.mainSources();
      if (files.isPresent()) {
        for (FileIngredient sourceFile : files.get()) {
          mainSources.add(sourceFile.source);
        }
        importGraph = new CssImportGraph(log, mainSources.build());
      } else {
        throw new MojoExecutionException("Failed to resolve sources");
      }
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to parse imports in CSS source files", ex);
    }

    for (Sources.Source entryPoint : importGraph.entryPoints) {
      Dependencies deps = importGraph.transitiveClosureDeps(
          log, entryPoint);
      if (!deps.foundAllStatic) {
        throw new MojoExecutionException(
            "Failed to resolve all dependencies of "
                + entryPoint.canonicalPath);
      }
      CssOptions.Outputs cssCompilerOutputs = new CssOptions.Outputs(
          cssOpts, entryPoint,
          defaultCssOutputPathTemplate.value,
          defaultCssSourceMapPathTemplate.value);
      b.add(new CssBundle(
          cssOpts.id, entryPoint, deps.allDependencies,
          cssCompilerOutputs));
    }

    ImmutableList<CssBundle> bundles = b.build();

    OutputAmbiguityChecker.requireOutputsUnambiguous(
        log,
        Iterables.concat(
            Lists.transform(
                bundles,
                new Function<CssBundle, List<OutputAmbiguityChecker.Output>>() {
                  public
                  List<OutputAmbiguityChecker.Output> apply(CssBundle bundle) {
                    return bundle.outputs.allOutputs();
                  }
                })));

    SerializedObjectIngredient<CssBundleList> bundleList =
        ((SerializedObjectIngredient<?>) outputs.get(0))
        .asSuperType(CssBundleList.class);
    bundleList.setStoredObject(new CssBundleList(bundles));
    try {
      bundleList.write();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to write bundle list", ex);
    }
  }

  @Override
  public void skip(Log log) throws MojoExecutionException {
    SerializedObjectIngredient<CssBundleList> bundleList =
        ((SerializedObjectIngredient<?>) outputs.get(0))
        .asSuperType(CssBundleList.class);
    try {
      bundleList.read();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to read bundle list", ex);
    }
  }

  @Override
  public ImmutableList<Step> extraSteps(Log log)
  throws MojoExecutionException {
    SerializedObjectIngredient<CssBundleList> bundleList =
        ((SerializedObjectIngredient<?>) outputs.get(0))
        .asSuperType(CssBundleList.class);
    OptionsIngredient<CssOptions> options =
        ((OptionsIngredient<?>) inputs.get(0))
        .asSuperType(CssOptions.class);
    StringValue renameMapFilePath = (StringValue) inputs.get(4);

    // For each entry point, we need to schedule a compile.
    ImmutableList.Builder<Step> extraSteps = ImmutableList.builder();
    for (CssBundle b : bundleList.getStoredObject().get().bundles) {

      SerializedObjectIngredient<CssBundle> bundle;
      FileIngredient cssFile;
      FileIngredient sourceMapFile;
      FileIngredient renameMapFile;
      try {
        bundle = ingredients.serializedObject(
            b.entryPoint.reroot(cssOutputDirectory).suffix(".bundle.ser"),
            CssBundle.class);
        bundle.setStoredObject(b);
        bundle.write();

        cssFile = ingredients.file(b.outputs.css);
        sourceMapFile = ingredients.file(b.outputs.sourceMap);
        renameMapFile = ingredients.file(new File(renameMapFilePath.value));
      } catch (IOException ex) {
        throw new MojoExecutionException(
            "Failed to create intermediate inputs for "
            + b.entryPoint.relativePath,
            ex);
      }

      ImmutableList.Builder<FileIngredient> inputFiles =
          ImmutableList.builder();
      for (Source inputFile : b.inputs) {
        inputFiles.add(ingredients.file(inputFile));
      }

      extraSteps.add(new CompileOneBundle(
          substMap,
          options,
          bundle,
          inputFiles.build(),
          cssFile,
          sourceMapFile,
          renameMapFile));
    }
    return extraSteps.build();
  }
}
