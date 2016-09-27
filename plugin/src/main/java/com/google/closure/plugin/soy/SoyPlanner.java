package com.google.closure.plugin.soy;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.closure.plugin.common.FileExt;
import com.google.closure.plugin.common.OptionsUtils;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;

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
  public PlanGraphNode<?> plan(SoyOptions unprepared)
  throws MojoExecutionException {
    SoyOptions opts = OptionsUtils.prepareOne(unprepared);

    BuildSoyFileSet soyPlanRoot = new BuildSoyFileSet(context);
    soyPlanRoot.setOptionSets(ImmutableList.of(opts));
    // Run after node that extracts .soy files from <dependency>'s JARs
    joinNodes.pipeline(
        ImmutableSortedSet.of(FileExt.SOY),
        soyPlanRoot,
        ImmutableSortedSet.of(FileExt.CLASS, FileExt.JS));
    return soyPlanRoot;
  }
}
