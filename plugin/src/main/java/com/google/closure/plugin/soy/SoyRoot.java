package com.google.closure.plugin.soy;

import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.OptionPlanGraphNode;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;

final class SoyRoot extends OptionPlanGraphNode<SoyOptions> {

  protected SoyRoot(PlanContext context) {
    super(context);
  }

  @Override
  protected SV getStateVector() {
    return new SV(this);
  }

  static final class SV
  extends OptionPlanGraphNode.OptionStateVector<SoyOptions> {

    private static final long serialVersionUID = 1L;

    protected SV(OptionPlanGraphNode<SoyOptions> node) {
      super(node);
    }

    @Override
    public PlanGraphNode<?> reconstitute(PlanContext c, JoinNodes joinNodes) {
      return apply(new SoyRoot(c));
    }
  }
}
