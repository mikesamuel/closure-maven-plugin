package com.google.closure.plugin.css;

import com.google.common.collect.ImmutableList;

import com.google.closure.plugin.common.Sources;
import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.plan.BundlingPlanGraphNode;

final class CssBundle
implements BundlingPlanGraphNode.Bundle {

  private static final long serialVersionUID = 1756086919632313649L;

  final String optionsId;
  final Sources.Source entryPoint;
  final ImmutableList<Sources.Source> inputs;
  final CssOptions.Outputs outputs;

  CssBundle(
      String optionsId,
      Sources.Source entryPoint,
      ImmutableList<Sources.Source> inputs,
      CssOptions.Outputs outputs) {
    this.optionsId = optionsId;
    this.entryPoint = entryPoint;
    this.inputs = inputs;
    this.outputs = outputs;
  }

  @Override
  public ImmutableList<Source> getInputs() {
    return inputs;
  }
}