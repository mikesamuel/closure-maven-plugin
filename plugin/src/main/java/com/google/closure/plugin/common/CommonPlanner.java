package com.google.closure.plugin.common;

import java.io.File;

import org.apache.maven.plugin.logging.Log;

import com.google.common.collect.ImmutableList;
import com.google.closure.plugin.common.Ingredients.HashedInMemory;
import com.google.closure.plugin.common.Ingredients.PathValue;
import com.google.closure.plugin.plan.HashStore;
import com.google.closure.plugin.plan.Plan;
import com.google.closure.plugin.plan.Step;

/**
 * Common plan elements that are used by the various language type planners..
 */
public class CommonPlanner {

  /** Logger that should receive events related to step execution. */
  public final Log log;
  /** The maven project base directory. */
  public final File baseDir;
  /** The maven {@code target} directory. */
  public final File outputDir;
  /** The common CSS identifier substitution map provider. */
  public final StableCssSubstitutionMapProvider substitutionMapProvider;
  /** Factory for all ingredients for plan steps. */
  public final Ingredients ingredients;
  /** Where to put generated files. */
  public final HashedInMemory<GenfilesDirs> genfiles;
  /** The {@code target/classes} directory. */
  public final PathValue projectBuildOutputDirectory;
  /** The {@code target/classses/closure}. */
  public final PathValue closureOutputDirectory;


  /**
   * May be used by steps to run a compiler but stubbed out in tests.
   */
  public ProcessRunner processRunner = DefaultProcessRunner.INSTANCE;
  private final HashStore hashStore;
  private final ImmutableList.Builder<Step> steps;

  /** */
  public CommonPlanner(
      Log log, File baseDir, File outputDir, File projectBuildOutputDirectory,
      File closureOutputDirectory,
      StableCssSubstitutionMapProvider substitutionMapProvider,
      HashStore hashStore, Ingredients ingredients, GenfilesDirs genfiles) {
    this.ingredients = ingredients;
    this.log = log;
    this.baseDir = baseDir;
    this.outputDir = outputDir;
    this.projectBuildOutputDirectory = ingredients.pathValue(
        projectBuildOutputDirectory);
    this.closureOutputDirectory = ingredients.pathValue(closureOutputDirectory);
    this.substitutionMapProvider = substitutionMapProvider;
    this.genfiles = ingredients.hashedInMemory(GenfilesDirs.class, genfiles);

    this.hashStore = hashStore;
    this.steps = ImmutableList.builder();
  }

  /** Add a step to the plan. */
  public void addStep(Step step) {
    this.steps.add(step);
  }

  /** A plan with all the steps added. */
  public Plan toPlan() {
    return new Plan(log, hashStore, steps.build());
  }
}
