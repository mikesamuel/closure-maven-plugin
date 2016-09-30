package com.google.closure.plugin.proto;

import com.google.closure.plugin.common.DirectoryScannerSpec;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.OptionPlanGraphNode;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;

final class ProtoRoot extends OptionPlanGraphNode<ProtoFinalOptions> {

  ProtoRoot(PlanContext context) {
    super(context);
  }

  @Override
  protected
  DirectoryScannerSpec getScannerSpecForOptions(ProtoFinalOptions opts) {
    return opts.sources;
  }

  @Override
  protected SV getStateVector() {
    return new SV(this);
  }

  static final class SV
  extends OptionPlanGraphNode.OptionStateVector<ProtoFinalOptions> {
    private static final long serialVersionUID = 1L;

    protected SV(ProtoRoot node) {
      super(node);
    }

    @Override
    public PlanGraphNode<?> reconstitute(PlanContext c, JoinNodes joinNodes) {
      return apply(new ProtoRoot(c));
    }
  }
}
