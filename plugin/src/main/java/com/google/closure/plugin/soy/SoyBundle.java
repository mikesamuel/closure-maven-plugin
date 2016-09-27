package com.google.closure.plugin.soy;

import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.plan.BundlingPlanGraphNode.Bundle;
import com.google.common.collect.ImmutableList;

final class SoyBundle implements Bundle {
  private static final long serialVersionUID = 7321380130637611252L;

  final ImmutableList<Source> inputs;
  final SoyFileSetSupplier sfsSupplier;

  SoyBundle(ImmutableList<Source> inputs, SoyFileSetSupplier sfsSupplier) {
    this.inputs = inputs;
    this.sfsSupplier = sfsSupplier;
  }

  @Override
  public ImmutableList<Source> getInputs() {
    return inputs;
  }
}
