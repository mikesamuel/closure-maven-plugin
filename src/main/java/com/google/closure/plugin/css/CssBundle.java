package com.google.closure.plugin.css;

import java.io.Serializable;

import com.google.common.collect.ImmutableList;
import com.google.closure.plugin.common.Sources;

final class CssBundle implements Serializable {

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
}