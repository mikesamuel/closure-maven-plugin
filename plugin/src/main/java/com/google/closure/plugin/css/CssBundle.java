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

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((entryPoint == null) ? 0 : entryPoint.hashCode());
    result = prime * result + ((inputs == null) ? 0 : inputs.hashCode());
    result = prime * result + ((optionsId == null) ? 0 : optionsId.hashCode());
    result = prime * result + ((outputs == null) ? 0 : outputs.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    CssBundle other = (CssBundle) obj;
    if (entryPoint == null) {
      if (other.entryPoint != null) {
        return false;
      }
    } else if (!entryPoint.equals(other.entryPoint)) {
      return false;
    }
    if (inputs == null) {
      if (other.inputs != null) {
        return false;
      }
    } else if (!inputs.equals(other.inputs)) {
      return false;
    }
    if (optionsId == null) {
      if (other.optionsId != null) {
        return false;
      }
    } else if (!optionsId.equals(other.optionsId)) {
      return false;
    }
    if (outputs == null) {
      if (other.outputs != null) {
        return false;
      }
    } else if (!outputs.equals(other.outputs)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "{CssBundle " + this.optionsId
        + " entryPoint=" + this.entryPoint.relativePath + "}";
  }
}