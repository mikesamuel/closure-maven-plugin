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

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((inputs == null) ? 0 : inputs.hashCode());
    result = prime * result + ((sfsSupplier == null) ? 0 : sfsSupplier.hashCode());
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
    SoyBundle other = (SoyBundle) obj;
    if (inputs == null) {
      if (other.inputs != null) {
        return false;
      }
    } else if (!inputs.equals(other.inputs)) {
      return false;
    }
    if (sfsSupplier == null) {
      if (other.sfsSupplier != null) {
        return false;
      }
    } else if (!sfsSupplier.equals(other.sfsSupplier)) {
      return false;
    }
    return true;
  }
}
