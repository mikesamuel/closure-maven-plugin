package com.google.closure.plugin.css;

import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.OptionPlanGraphNode;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;

/**
 * Fans out options to bundling nodes.
 */
final class ListOptions extends OptionPlanGraphNode<CssOptions> {

  ListOptions(PlanContext context) {
    super(context);
  }

  @Override
  protected SV getStateVector() {
    return new SV(this);
  }

  static final class SV
  extends OptionStateVector<CssOptions> {
    private static final long serialVersionUID = 1L;

    SV(ListOptions lo) {
      super(lo);
    }

    @Override
    public PlanGraphNode<?> reconstitute(PlanContext context, JoinNodes jn) {
      return apply(new ListOptions(context));
    }
  }
}
