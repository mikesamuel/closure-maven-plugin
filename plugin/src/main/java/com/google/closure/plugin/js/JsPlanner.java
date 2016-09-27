package com.google.closure.plugin.js;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.closure.plugin.common.FileExt;
import com.google.closure.plugin.common.OptionsUtils;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;

/**
 * A planner that invokes the closure compiler on JavaScript sources.
 */
public final class JsPlanner {
  final PlanContext context;
  final JoinNodes joinNodes;

  /** */
  public JsPlanner(PlanContext context, JoinNodes joinNodes) {
    this.context = context;
    this.joinNodes = joinNodes;
  }

  /**
   * Adds steps to a common planner to find JS sources, extract a set of module
   * definitions, and invoke the closure compiler to build them.
   */
  public PlanGraphNode<?> plan(Iterable<? extends JsOptions> unpreparedJs)
  throws MojoExecutionException {

    ImmutableList<JsOptions> js = OptionsUtils.prepare(
        new Supplier<JsOptions>() {
          @Override
          public JsOptions get() {
            return new JsOptions();
          }
        },
        unpreparedJs);

    joinNodes.pipeline(
        ImmutableSortedSet.of(FileExt.JSON),
        new LinkCssNameMap(context),
        ImmutableSortedSet.of(FileExt.JS));

    Fanout fanout = new Fanout(context);
    fanout.setOptionSets(js);

    joinNodes.pipeline(
        ImmutableSortedSet.of(FileExt.JS),
        fanout,
        ImmutableSortedSet.of(FileExt._ANY));

    return fanout;
  }
}
