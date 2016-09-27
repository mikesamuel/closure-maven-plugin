package com.google.closure.plugin.js;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.OptionPlanGraphNode;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;
import com.google.common.collect.ImmutableList;

final class Fanout extends OptionPlanGraphNode<JsOptions> {

  protected Fanout(PlanContext context) {
    super(context);
  }

  @Override
  protected PlanGraphNode<?> fanOutTo(JsOptions options)
  throws MojoExecutionException {
    return new ComputeJsDepInfo(context, options);
  }

  @Override
  protected JsOptions getOptionsForFollower(PlanGraphNode<?> follower) {
    return ((ComputeJsDepInfo) follower).options;
  }

  @Override
  protected SV getStateVector() {
    return new SV(ImmutableList.copyOf(this.getOptionSets()));
  }


  static final class SV
  extends OptionPlanGraphNode.OptionStateVector<JsOptions> {

    private static final long serialVersionUID = 1L;

    protected SV(ImmutableList<JsOptions> optionSets) {
      super(optionSets);
    }

    @Override
    public PlanGraphNode<?> reconstitute(PlanContext c, JoinNodes jn) {
      Fanout fo = new Fanout(c);
      fo.setOptionSets(optionSets);
      return fo;
    }
  }
}
