package com.google.closure.plugin.css;

import com.google.common.collect.ImmutableList;
import com.google.closure.plugin.common.Sources;
import com.google.closure.plugin.plan.KeyedSerializable;
import com.google.closure.plugin.plan.PlanKey;

final class CssBundle implements KeyedSerializable {

  private static final long serialVersionUID = 1756086919632313649L;

  final String optionsId;
  final Sources.Source entryPoint;
  final ImmutableList<Sources.Source> inputs;
  final CssOptions.Outputs outputs;
  private transient PlanKey key;

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
  public PlanKey getKey() {
    if (key == null) {
      key = PlanKey.builder("plan-key")
        .addString(optionsId)
        .addString(entryPoint.canonicalPath.getPath())
        .build();
    }
    return key;
  }
}