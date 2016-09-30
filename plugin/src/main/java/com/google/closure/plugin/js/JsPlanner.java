package com.google.closure.plugin.js;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.closure.plugin.common.FileExt;
import com.google.closure.plugin.common.OptionsUtils;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;

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
  public void plan(Iterable<? extends JsOptions> unpreparedJs)
  throws MojoExecutionException {

    ImmutableList<JsOptions> js = OptionsUtils.prepare(
        new Supplier<JsOptions>() {
          @Override
          public JsOptions get() {
            return new JsOptions();
          }
        },
        unpreparedJs);


    joinNodes.pipeline()
        .require(FileExt.JSON)
        .then(new LinkCssNameMap(context))
        .provide(FileExt.JS)
        .build();

    JsResolveInputs inputResolver = new JsResolveInputs(context);
    inputResolver.setOptionSets(js);

    ComputeJsDepInfo depInfo = new ComputeJsDepInfo(context);

    ComputeJsDepGraph depGraph = new ComputeJsDepGraph(context);

    CompileJs compileJs = new CompileJs(context);

    joinNodes.pipeline()
        .require(FileExt.JS)
        .then(inputResolver)
        .then(depInfo)
        .then(depGraph)
        .then(compileJs)
        .provide(FileExt._ANY)
        .build();
  }
}
