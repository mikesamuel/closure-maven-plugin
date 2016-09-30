package com.google.closure.plugin.js;

import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.OptionPlanGraphNode;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;

final class JsResolveInputs extends OptionPlanGraphNode<JsOptions> {

  protected JsResolveInputs(PlanContext context) {
    super(context);
  }

  @Override
  protected SV getStateVector() {
    return new SV(this);
  }


  static final class SV
  extends OptionPlanGraphNode.OptionStateVector<JsOptions> {

    private static final long serialVersionUID = 1L;

    protected SV(JsResolveInputs node) {
      super(node);
    }

    @Override
    public PlanGraphNode<?> reconstitute(PlanContext c, JoinNodes jn) {
      return apply(new JsResolveInputs(c));
    }
  }
}
