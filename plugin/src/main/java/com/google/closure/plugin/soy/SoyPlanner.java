package com.google.closure.plugin.soy;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.collect.ImmutableList;
import com.google.closure.plugin.common.FileExt;
import com.google.closure.plugin.common.OptionsUtils;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;

/**
 * Adds steps related to Soy template compilation.
 */
public final class SoyPlanner {
  private final PlanContext context;
  private final JoinNodes joinNodes;

  /** */
  public SoyPlanner(PlanContext context, JoinNodes joinNodes) {
    this.context = context;
    this.joinNodes = joinNodes;
  }

  /** Adds steps to the common planner to compiler soy. */
  public void plan(SoyOptions unprepared) throws MojoExecutionException {
    SoyOptions opts = OptionsUtils.prepareOne(unprepared);

    SoyRoot soyPlanRoot = new SoyRoot(context);
    soyPlanRoot.setOptionSets(ImmutableList.of(opts));
    // Run after node that extracts .soy files from <dependency>'s JARs
    joinNodes.pipeline()
        .require(
            // We need the soy files to compile
            FileExt.SOY,
            // The Soy type system needs to know about protobuffers.
            FileExt.PD)
        .then(soyPlanRoot)
        .then(new BuildSoyFileSet(context))
        .then(new SoyToJs(context), new SoyToJava(context))
        .provide(FileExt.CLASS, FileExt.JS)
        .build();
  }
}
