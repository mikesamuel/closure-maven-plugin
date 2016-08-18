package com.google.closure.plugin.genjava;

import java.io.File;

import com.google.closure.plugin.common.CommonPlanner;
import com.google.closure.plugin.common.DirectoryScannerSpec;
import com.google.closure.plugin.common.Ingredients;
import com.google.closure.plugin.common.Ingredients.DirScanFileSetIngredient;
import com.google.closure.plugin.common.Ingredients.PathValue;
import com.google.closure.plugin.common.TypedFile;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * Generates source files with constant values that correspond to compiler
 * outputs to ease linking applications together.
 */
public final class GenSymbolsPlanner {

  private final CommonPlanner planner;
  private String genJavaPackageName;

  /** */
  public GenSymbolsPlanner(CommonPlanner planner) {
    this.planner = planner;
  }

  /** Sets the package name in generated java files. */
  public GenSymbolsPlanner genJavaPackageName(String newPackageName) {
    this.genJavaPackageName = Preconditions.checkNotNull(newPackageName);
    return this;
  }

  /** Adds steps to the common planner. */
  public void plan() {
    Ingredients ingredients = planner.ingredients;
    DirectoryScannerSpec outputFilesSpec = new DirectoryScannerSpec(
        ImmutableList.of(new TypedFile(planner.closureOutputDirectory.value)),
        ImmutableList.of("**"),
        ImmutableList.<String>of());
    DirScanFileSetIngredient outputFiles = ingredients.fileset(outputFilesSpec);

    PathValue webFilesJava = ingredients.pathValue(
        javaSourcePath("WebFiles.java"));

    planner.addStep(new FindOutputFiles(
        outputFiles, webFilesJava,
        ingredients.stringValue(genJavaPackageName)));
  }

  private File javaSourcePath(String basename) {
    File dir = planner.genfiles.getValue().javaGenfiles;
    if (!genJavaPackageName.isEmpty()) {
      for (String packageDirName : genJavaPackageName.split("[.]")) {
        dir = new File(dir, packageDirName);
      }
    }
    return new File(dir, basename);
  }
}
