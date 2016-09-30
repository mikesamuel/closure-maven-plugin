package com.google.closure.plugin.genjava;

import java.io.File;

import com.google.closure.plugin.common.DirectoryScannerSpec;
import com.google.closure.plugin.common.FileExt;
import com.google.closure.plugin.common.TypedFile;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * Generates source files with constant values that correspond to compiler
 * outputs to ease linking applications together.
 */
public final class GenSymbolsPlanner {

  private final PlanContext context;
  private final JoinNodes joinNodes;
  private String genJavaPackageName;

  /** */
  public GenSymbolsPlanner(PlanContext context, JoinNodes joinNodes) {
    this.context = context;
    this.joinNodes = joinNodes;
  }

  /** Sets the package name in generated java files. */
  public GenSymbolsPlanner genJavaPackageName(String newPackageName) {
    this.genJavaPackageName = Preconditions.checkNotNull(newPackageName);
    return this;
  }

  /** Adds steps to the common planner. */
  public void plan() {
    DirectoryScannerSpec outputFilesSpec = new DirectoryScannerSpec(
        ImmutableList.of(new TypedFile(context.closureOutputDirectory)),
        ImmutableList.of("**"),
        ImmutableList.<String>of());

    File webFilesJava = javaSourcePath("WebFiles.java");

    GenJavaSymbols gjs = new GenJavaSymbols(
        context, outputFilesSpec, webFilesJava, genJavaPackageName);

    joinNodes.pipeline()
        .require(FileExt._ANY)
        .then(gjs)
        .build();
  }

  private File javaSourcePath(String basename) {
    File dir = context.genfilesDirs.javaGenfiles;
    if (!genJavaPackageName.isEmpty()) {
      for (String packageDirName : genJavaPackageName.split("[.]")) {
        dir = new File(dir, packageDirName);
      }
    }
    return new File(dir, basename);
  }
}
