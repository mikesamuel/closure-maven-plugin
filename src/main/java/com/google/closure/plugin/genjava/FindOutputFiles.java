package com.google.closure.plugin.genjava;

import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.closure.plugin.common.Ingredients.DirScanFileSetIngredient;
import com.google.closure.plugin.common.Ingredients.PathValue;
import com.google.closure.plugin.common.Ingredients.StringValue;
import com.google.closure.plugin.plan.Ingredient;
import com.google.closure.plugin.plan.PlanKey;
import com.google.closure.plugin.plan.Step;
import com.google.closure.plugin.plan.StepSource;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

final class FindOutputFiles extends Step {

  final DirScanFileSetIngredient outputFiles;
  final PathValue webFilesJava;
  final StringValue genJavaPackageName;

  FindOutputFiles(
      DirScanFileSetIngredient outputFiles,
      PathValue webFilesJava,
      StringValue genJavaPackageName) {
    super(
        PlanKey.builder("find-output_files").build(),
        ImmutableList.<Ingredient>of(),
        StepSource.ALL_COMPILED,
        Sets.immutableEnumSet(StepSource.JAVA_GENERATED));
    this.outputFiles = outputFiles;
    this.webFilesJava = webFilesJava;
    this.genJavaPackageName = genJavaPackageName;
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    doScan(log);
  }

  @Override
  public void skip(Log log) throws MojoExecutionException {
    doScan(log);
  }

  private void doScan(Log log) throws MojoExecutionException {
    try {
      outputFiles.resolve(log);
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to resolve output files", ex);
    }
  }

  @Override
  public ImmutableList<Step> extraSteps(Log log) throws MojoExecutionException {
    return ImmutableList.<Step>of(
        new GenJavaSymbols(
            outputFiles, webFilesJava,
            genJavaPackageName));
  }

}
