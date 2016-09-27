package com.google.closure.plugin.proto;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSortedSet;
import com.google.closure.plugin.common.FileExt;
import com.google.closure.plugin.common.OptionsUtils;
import com.google.closure.plugin.common.ToolFinder;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;

/**
 * Adds steps that feed .proto files to protoc.
 */
public final class ProtoPlanner {

  private final PlanContext context;
  private final JoinNodes joinNodes;
  private final ToolFinder<ProtoFinalOptions> protocFinder;
  private File defaultMainDescriptorFile;
  private File defaultTestDescriptorFile;

  /** */
  public ProtoPlanner(
      PlanContext context, JoinNodes joinNodes,
      ToolFinder<ProtoFinalOptions> protocFinder) {
    this.context = context;
    this.joinNodes = joinNodes;
    this.protocFinder = protocFinder;
  }


  /** Path for generated proto descriptor file set. */
  public ProtoPlanner defaultMainDescriptorFile(File f) {
    this.defaultMainDescriptorFile = f;
    return this;
  }

  /** Path for generated proto descriptor file set for test protos. */
  public ProtoPlanner defaultTestDescriptorFile(File f) {
    this.defaultTestDescriptorFile = f;
    return this;
  }

  /** Adds steps to the common planner. */
  public PlanGraphNode<?> plan(ProtoOptions opts)
  throws MojoExecutionException {
    Preconditions.checkNotNull(defaultMainDescriptorFile);
    Preconditions.checkNotNull(defaultTestDescriptorFile);
    Preconditions.checkNotNull(opts);

    ProtoFinalOptions protoOptions = OptionsUtils.prepareOne(opts).freeze(
        context,
        defaultMainDescriptorFile, defaultTestDescriptorFile);

    context.protoIO.mainDescriptorSetFile = Optional.of(
        Optional.fromNullable(protoOptions.descriptorSetFile)
        .or(defaultMainDescriptorFile));

    context.protoIO.testDescriptorSetFile = Optional.of(
        Optional.fromNullable(protoOptions.testDescriptorSetFile)
        .or(defaultTestDescriptorFile));

    context.protoIO.protocFinder = Optional.of(this.protocFinder);

    joinNodes.follows(new CopyProtosToJar(context), FileExt.PD);

    GenerateProtoPackageMap gppm = new GenerateProtoPackageMap(
        context, protoOptions);
    joinNodes.pipeline(
        ImmutableSortedSet.of(FileExt.PROTO),
        gppm,
        RunProtoc.FOLLOWER_EXTS);
    return gppm;
  }
}
