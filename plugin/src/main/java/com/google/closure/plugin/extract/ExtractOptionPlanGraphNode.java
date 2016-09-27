package com.google.closure.plugin.extract;

import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.OptionPlanGraphNode;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;
import com.google.common.collect.ImmutableList;

/**
 * An option set plan graph node that spawns nodes to resolve artifact names.
 */
public final class ExtractOptionPlanGraphNode
extends OptionPlanGraphNode<Extracts> {

  protected ExtractOptionPlanGraphNode(PlanContext context) {
    super(context);
  }

  @Override
  protected PlanGraphNode<?> fanOutTo(Extracts options) {
    return new ResolveExtracts(context, options);
  }

  @Override
  protected Extracts getOptionsForFollower(PlanGraphNode<?> follower) {
    ResolveExtracts re = (ResolveExtracts) follower;
    return re.options;
  }

  @Override
  protected SV getStateVector() {
    return new SV(ImmutableList.copyOf(this.getOptionSets()));
  }

  static final class SV
  extends OptionPlanGraphNode.OptionStateVector<Extracts> {

    private static final long serialVersionUID = -4602829620195836648L;

    SV(ImmutableList<Extracts> optionSets) {
      super(optionSets);
    }

    @SuppressWarnings("synthetic-access")
    @Override
    public PlanGraphNode<?> reconstitute(PlanContext context, JoinNodes jn) {
      ExtractOptionPlanGraphNode n = new ExtractOptionPlanGraphNode(context);
      for (Extracts e : this.optionSets) {
        n.addOptionSet(e);
      }
      return n;
    }

  }

}
