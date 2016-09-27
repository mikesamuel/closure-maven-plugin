package com.google.closure.plugin.css;

import com.google.common.collect.ImmutableList;
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
  protected FindEntryPoints fanOutTo(CssOptions options) {
    return new FindEntryPoints(context, options);
  }

  @Override
  protected CssOptions getOptionsForFollower(PlanGraphNode<?> follower) {
    FindEntryPoints f = (FindEntryPoints) follower;
    return f.getOptions();
  }

  @Override
  protected SV getStateVector() {
    return new SV(this.getOptionSets());
  }

  static final class SV
  extends OptionPlanGraphNode.OptionStateVector<CssOptions> {
    private static final long serialVersionUID = -5821057253659715417L;

    SV(Iterable<CssOptions> optionSets) {
      super(ImmutableList.copyOf(optionSets));
    }

    @SuppressWarnings("synthetic-access")
    @Override
    public PlanGraphNode<?> reconstitute(PlanContext context, JoinNodes jn) {
      ListOptions lo = new ListOptions(context);
      for (CssOptions optionSet : optionSets) {
        lo.addOptionSet(optionSet);
      }
      return lo;
    }
  }
}
