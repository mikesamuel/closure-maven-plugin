package com.google.closure.plugin.extract;

import com.google.closure.plugin.common.DirectoryScannerSpec;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.OptionPlanGraphNode;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;

final class ExtractRoot extends OptionPlanGraphNode<Extracts> {

  protected ExtractRoot(PlanContext context) {
    super(context);
  }

  @Override
  protected DirectoryScannerSpec getScannerSpecForOptions(Extracts opts) {
    return DirectoryScannerSpec.EMPTY;
  }

  @Override
  protected SV getStateVector() {
    return new SV(this);
  }

  static final class SV
  extends OptionPlanGraphNode.OptionStateVector<Extracts> {

    private static final long serialVersionUID = 1L;

    protected SV(ExtractRoot node) {
      super(node);
    }

    @Override
    public PlanGraphNode<?> reconstitute(PlanContext c, JoinNodes joinNodes) {
      return apply(new ExtractRoot(c));
    }
  }
}
