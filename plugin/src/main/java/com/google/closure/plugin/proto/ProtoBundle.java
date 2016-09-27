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
}
