package com.google.closure.plugin.proto;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.closure.plugin.common.FileExt;
import com.google.closure.plugin.common.OptionsUtils;
import com.google.closure.plugin.common.ToolFinder;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;

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

  /** Adds steps to the plan graph. */
  public ProtoFinalOptions prepare(ProtoOptions opts)
  throws MojoExecutionException {
    Preconditions.checkNotNull(defaultMainDescriptorFile);
    Preconditions.checkNotNull(defaultTestDescriptorFile);

    ProtoFinalOptions protoOptions =
        OptionsUtils.prepareOne(opts != null ? opts : new ProtoOptions())
        .freeze(
            context,
            defaultMainDescriptorFile, defaultTestDescriptorFile);

    context.protoIO.mainDescriptorSetFile = Optional.of(
        Optional.fromNullable(protoOptions.descriptorSetFile)
        .or(defaultMainDescriptorFile));

    context.protoIO.testDescriptorSetFile = Optional.of(
        Optional.fromNullable(protoOptions.testDescriptorSetFile)
        .or(defaultTestDescriptorFile));

    context.protoIO.protocFinder = Optional.of(this.protocFinder);
    return protoOptions;
  }

  /** Adds steps to the plan graph. */
  public void plan(ProtoFinalOptions protoOptions) {
    joinNodes.pipeline()
         .require(FileExt.PD)
         .then(new CopyProtosToJar(context))
         .build();

    ProtoRoot pr = new ProtoRoot(context);
    pr.setOptionSets(ImmutableList.of(protoOptions));

    joinNodes.pipeline()
        .require(FileExt.PROTO)
        .then(pr)
        .then(new GenerateProtoPackageMap(context))
        .then(new ProtoBundler(context))
        .then(new RunProtoc(context))
        .provide(FileExt.JAVA, FileExt.JS, FileExt.PD)
        .build();
  }
}
