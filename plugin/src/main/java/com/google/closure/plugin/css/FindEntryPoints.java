package com.google.closure.plugin.css;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.css.SubstitutionMapProvider;
import com.google.closure.plugin.common.Ingredients;
import com.google.closure.plugin.common.Sources;
import com.google.closure.plugin.common.Ingredients
    .DirScanFileSetIngredient;
import com.google.closure.plugin.common.Ingredients.FileIngredient;
import com.google.closure.plugin.common.Ingredients.HashedInMemory;
import com.google.closure.plugin.common.Ingredients.PathValue;
import com.google.closure.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.closure.plugin.common.Ingredients.StringValue;
import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.css.CssImportGraph.Dependencies;
import com.google.closure.plugin.plan.Ingredient;
import com.google.closure.plugin.plan.PlanKey;
import com.google.closure.plugin.plan.Step;
import com.google.closure.plugin.plan.StepSource;

final class FindEntryPoints extends Step {
  final SubstitutionMapProvider substMap;
  final Ingredients ingredients;
  final SerializedObjectIngredient<CssBundleList> bundleList;

  FindEntryPoints(
      SubstitutionMapProvider substMap,
      Ingredients ingredients,
      HashedInMemory<CssOptions> options,
      DirScanFileSetIngredient cssSources,
      StringValue defaultCssOutputPathTemplate,
      StringValue defaultCssSourceMapTemplate,
      SerializedObjectIngredient<CssBundleList> bundleList,
      PathValue cssOutputDirectory) {
    super(
        PlanKey.builder("css-find-entry-points")
            .addInp(options, cssSources, cssOutputDirectory).build(),
        ImmutableList.<Ingredient>of(
            options,
            cssSources,
            defaultCssOutputPathTemplate,
            defaultCssSourceMapTemplate,
            cssOutputDirectory),
        Sets.immutableEnumSet(StepSource.CSS_SRC, StepSource.CSS_GENERATED),
        Sets.immutableEnumSet(
            // Implicitly by the extra steps it schedules.
            StepSource.CSS_COMPILED,
            StepSource.CSS_SOURCE_MAP,
            StepSource.CSS_RENAME_MAP,
            StepSource.JS_GENERATED));
    this.substMap = substMap;
    this.ingredients = ingredients;
    this.bundleList = bundleList;
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    ImmutableList.Builder<CssBundle> b = ImmutableList.builder();

    Preconditions.checkState(inputs.size() == 5);
    CssOptions cssOpts = ((HashedInMemory<?>) inputs.get(0))
        .asSuperType(CssOptions.class)
        .getValue();
    DirScanFileSetIngredient cssSources =
        (DirScanFileSetIngredient) inputs.get(1);
    StringValue defaultCssOutputPathTemplate = (StringValue) inputs.get(2);
    StringValue defaultCssSourceMapPathTemplate = (StringValue) inputs.get(3);
    PathValue cssOutputDirectory = (PathValue) inputs.get(4);

    CssImportGraph importGraph;
    try {
      ImmutableList.Builder<Source> sources = ImmutableList.builder();
      for (FileIngredient sourceFile : cssSources.sources()) {
        sources.add(sourceFile.source);
      }
      importGraph = new CssImportGraph(log, sources.build());
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
      String basePath = cssOutputDirectory.value.getPath();
      if (!(basePath.isEmpty() || basePath.endsWith(File.separator))) {
        basePath += File.separator;
      }
      CssOptions.Outputs cssCompilerOutputs = new CssOptions.Outputs(
          cssOpts, entryPoint,
          basePath + defaultCssOutputPathTemplate.value,
          basePath + defaultCssSourceMapPathTemplate.value);
      b.add(new CssBundle(
          cssOpts.getId(), entryPoint, deps.allDependencies,
          cssCompilerOutputs));
    }

    ImmutableList<CssBundle> bundles = b.build();

    bundleList.setStoredObject(new CssBundleList(bundles));
    try {
      bundleList.write();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to write bundle list", ex);
    }
  }

  @Override
  public void skip(Log log) throws MojoExecutionException {
    try {
      bundleList.read();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to read bundle list", ex);
    }
  }

  @Override
  public ImmutableList<Step> extraSteps(Log log)
  throws MojoExecutionException {
    HashedInMemory<CssOptions> optionsIng =
        ((HashedInMemory<?>) inputs.get(0))
        .asSuperType(CssOptions.class);

    // For each entry point, we need to schedule a compile.
    ImmutableList.Builder<Step> extraSteps = ImmutableList.builder();
    for (CssBundle b : bundleList.getStoredObject().get().bundles) {

      HashedInMemory<CssBundle> bundleIng = ingredients.hashedInMemory(
          CssBundle.class, b);

      ImmutableList.Builder<FileIngredient> inputFiles =
          ImmutableList.builder();
      for (Source inputFile : b.inputs) {
        inputFiles.add(ingredients.file(inputFile));
      }

      extraSteps.add(new CompileOneBundle(
          substMap,
          optionsIng,
          bundleIng,
          ingredients.bundle(inputFiles.build())));
    }
    return extraSteps.build();
  }
}
