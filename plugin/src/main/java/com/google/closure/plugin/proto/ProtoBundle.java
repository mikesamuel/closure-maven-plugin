package com.google.closure.plugin.proto;

import java.io.File;

import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.plan.BundlingPlanGraphNode.Bundle;
import com.google.closure.plugin.proto.RunProtoc.LangSet;
import com.google.closure.plugin.proto.RunProtoc.RootSet;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

final class ProtoBundle implements Bundle {
  private static final long serialVersionUID = -6825220160946608037L;

  final RootSet rootSet;
  final LangSet langSet;
  final ImmutableList<Source> inputs;
  final Optional<File> descriptorSetFile;

  ProtoBundle(
      RootSet rootSet,
      LangSet langSet,
      ImmutableList<Source> inputs,
      Optional<File> descriptorSetFile) {

    this.rootSet = rootSet;
    this.langSet = langSet;
    this.inputs = inputs;
    this.descriptorSetFile = descriptorSetFile;
  }

  @Override
  public ImmutableCollection<Source> getInputs() {
    return inputs;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((descriptorSetFile == null) ? 0 : descriptorSetFile.hashCode());
    result = prime * result + ((inputs == null) ? 0 : inputs.hashCode());
    result = prime * result + ((langSet == null) ? 0 : langSet.hashCode());
    result = prime * result + ((rootSet == null) ? 0 : rootSet.hashCode());
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
    ProtoBundle other = (ProtoBundle) obj;
    if (descriptorSetFile == null) {
      if (other.descriptorSetFile != null) {
        return false;
      }
    } else if (!descriptorSetFile.equals(other.descriptorSetFile)) {
      return false;
    }
    if (inputs == null) {
      if (other.inputs != null) {
        return false;
      }
    } else if (!inputs.equals(other.inputs)) {
      return false;
    }
    if (langSet != other.langSet) {
      return false;
    }
    if (rootSet != other.rootSet) {
      return false;
    }
    return true;
  }
}
