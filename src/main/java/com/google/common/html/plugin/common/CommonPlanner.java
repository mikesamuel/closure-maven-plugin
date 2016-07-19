package com.google.common.html.plugin.common;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.logging.Log;

import com.google.common.collect.ImmutableList;
import com.google.common.css.SubstitutionMapProvider;
import com.google.common.html.plugin.plan.HashStore;
import com.google.common.html.plugin.plan.Plan;
import com.google.common.html.plugin.plan.Step;

public class CommonPlanner {

  public final Log log;
  public final File outputDir;
  public final SubstitutionMapProvider substitutionMapProvider;
  public final Ingredients ingredients;
  public final Ingredients.SerializedObjectIngredient<GenfilesDirs> genfiles;

  private final HashStore hashStore;
  private final ImmutableList.Builder<Step> steps;

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


  public void addStep(Step step) {
    this.steps.add(step);
  }

  public Plan toPlan() {
    return new Plan(log, hashStore, steps.build());
  }
}
