package com.google.common.html.plugin.css;

import java.io.IOException;
import java.io.Serializable;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.html.plugin.Sources;
import com.google.common.html.plugin.plan.Hash;
import com.google.common.html.plugin.plan.Ingredient;

final class CssBundle extends Ingredient implements Serializable {

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
    super("css-bundle:" + optionsId + ";" + entryPoint.canonicalPath);
    this.optionsId = optionsId;
    this.entryPoint = entryPoint;
    this.inputs = inputs;
    this.outputs = outputs;
  }

  public Optional<Hash> hash() throws IOException {
    return Optional.of(Hash.hashSerializable(this));
  }
}