package com.google.common.html.plugin.common;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.logging.Log;

import com.google.common.collect.ImmutableList;
import com.google.common.css.SubstitutionMapProvider;
import com.google.common.html.plugin.plan.HashStore;
import com.google.common.html.plugin.plan.Plan;
import com.google.common.html.plugin.plan.Step;

/**
 * Common plan elements that are used by the various language type planners..
 */
public class CommonPlanner {

  /** Logger that should receive events related to step execution. */
  public final Log log;
  /** The maven {@code target} directory. */
  public final File outputDir;
  /** The common CSS identifier substitution map provider. */
  public final SubstitutionMapProvider substitutionMapProvider;
  /** Factory for all ingredients for plan steps. */
  public final Ingredients ingredients;
  /** Where to put generated files. */
  public final Ingredients.SerializedObjectIngredient<GenfilesDirs> genfiles;
  /**
   * May be used by steps to run a compiler but stubbed out in tests.
   */
  public ProcessRunner processRunner = DefaultProcessRunner.INSTANCE;

  private final HashStore hashStore;
  private final ImmutableList.Builder<Step> steps;

  /** */
  public CommonPlanner(
      Log log, File outputDir,
      SubstitutionMapProvider substitutionMapProvider,
      HashStore hashStore)
  throws IOException {
    this.log = log;
    this.outputDir = outputDir;
    this.substitutionMapProvider = substitutionMapProvider;
    this.ingredients = new Ingredients();
    this.genfiles = ingredients.serializedObject(
        new File(outputDir, "closure-genfiles.ser"), GenfilesDirs.class);

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
