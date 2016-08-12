package com.google.common.html.plugin.css;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.css.SubstitutionMapProvider;
import com.google.common.html.plugin.common.Ingredients;
import com.google.common.html.plugin.common.Sources;
import com.google.common.html.plugin.common.Ingredients
    .DirScanFileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.FileIngredient;
import com.google.common.html.plugin.common.Ingredients.HashedInMemory;
import com.google.common.html.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.common.html.plugin.common.Ingredients.StringValue;
import com.google.common.html.plugin.common.Sources.Source;
import com.google.common.html.plugin.common.TypedFile;
import com.google.common.html.plugin.css.CssImportGraph.Dependencies;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.PlanKey;
import com.google.common.html.plugin.plan.Step;
import com.google.common.html.plugin.plan.StepSource;

final class FindEntryPoints extends Step {
  final SubstitutionMapProvider substMap;
  final Ingredients ingredients;
  final File cssOutputDirectory;
  final SerializedObjectIngredient<CssBundleList> bundleList;

  FindEntryPoints(
      SubstitutionMapProvider substMap,
      Ingredients ingredients,
      File cssOutputDirectory,
      HashedInMemory<CssOptions> options,
      DirScanFileSetIngredient cssSources,
      StringValue defaultCssOutputPathTemplate,
      StringValue defaultCssSourceMapTemplate,
      SerializedObjectIngredient<CssBundleList> bundleList) {
    super(
        PlanKey.builder("css-find-entry-points")
            .addInp(options, cssSources).build(),
        ImmutableList.<Ingredient>of(
            options,
            cssSources,
            defaultCssOutputPathTemplate,
            defaultCssSourceMapTemplate),
        Sets.immutableEnumSet(StepSource.CSS_SRC, StepSource.CSS_GENERATED),
        ImmutableSet.<StepSource>of());
    this.substMap = substMap;
    this.ingredients = ingredients;
    this.cssOutputDirectory = cssOutputDirectory;
    this.bundleList = bundleList;
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    ImmutableList.Builder<CssBundle> b = ImmutableList.builder();

    Preconditions.checkState(inputs.size() == 4);
    CssOptions cssOpts = ((HashedInMemory<?>) inputs.get(0))
        .asSuperType(CssOptions.class)
        .getValue();
    DirScanFileSetIngredient cssSources =
        (DirScanFileSetIngredient) inputs.get(1);
    StringValue defaultCssOutputPathTemplate = (StringValue) inputs.get(2);
    StringValue defaultCssSourceMapPathTemplate = (StringValue) inputs.get(3);

    CssImportGraph importGraph;
    try {
      cssSources.resolve(log);

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
      CssOptions.Outputs cssCompilerOutputs = new CssOptions.Outputs(
          cssOpts, entryPoint,
          defaultCssOutputPathTemplate.value,
          defaultCssSourceMapPathTemplate.value);
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
    HashedInMemory<CssOptions> options =
        ((HashedInMemory<?>) inputs.get(0))
        .asSuperType(CssOptions.class);

    // For each entry point, we need to schedule a compile.
    ImmutableList.Builder<Step> extraSteps = ImmutableList.builder();
    for (CssBundle b : bundleList.getStoredObject().get().bundles) {

      SerializedObjectIngredient<CssBundle> bundle;
      try {
        bundle = ingredients.serializedObject(
            b.entryPoint.reroot(new TypedFile(cssOutputDirectory))
                .suffix(".bundle.ser"),
            CssBundle.class);
        bundle.setStoredObject(b);
        bundle.write();
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
          b.outputs.css,
          substMap,
          options,
          bundle,
          inputFiles.build()));
    }
    return extraSteps.build();
  }
}
