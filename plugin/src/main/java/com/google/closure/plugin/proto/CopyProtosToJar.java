package com.google.closure.plugin.proto;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.closure.module.ClosureModule;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

final class CopyProtosToJar extends PlanGraphNode<CopyProtosToJar.SV> {

  private static final String DESCRIPTORS_FILE_BASENAME =
      ClosureModule.PROTO_DESCRIPTORS_RESOURCE_PATH.substring(
          ClosureModule.PROTO_DESCRIPTORS_RESOURCE_PATH.lastIndexOf('/') + 1);

  private Optional<File> toCopy = Optional.absent();
  private Optional<File> descriptorOutputFile = Optional.absent();

  CopyProtosToJar(PlanContext context) {
    super(context);
  }

  @Override
  protected void preExecute(Iterable<? extends PlanGraphNode<?>> preceders) {
    // Nop
  }

  @Override
  protected void filterUpdates() throws IOException, MojoExecutionException {
    File f = context.protoIO.mainDescriptorSetFile.get();
    if (!context.buildContext.isIncremental()
        || context.buildContext.hasDelta(f)) {
      this.toCopy = Optional.of(f);
    } else {
      this.toCopy = Optional.absent();
    }
  }

  @Override
  protected Iterable<? extends File> changedOutputFiles() {
    if (descriptorOutputFile.isPresent()) {
      return ImmutableList.of(descriptorOutputFile.get());
    }
    return ImmutableList.of();
  }

  @Override
  protected void process() throws IOException, MojoExecutionException {
    this.descriptorOutputFile = Optional.absent();

    if (!this.toCopy.isPresent()) {
      return;
    }

    File descriptorSetFile = toCopy.get();
    if (descriptorSetFile.exists()) {
      File outputFile = new File(
          context.closureOutputDirectory,
          DESCRIPTORS_FILE_BASENAME);
      // Not generated if protoc is not run.
      try {
        Files.createParentDirs(outputFile);
        Files.copy(
            descriptorSetFile,
            outputFile);
      } catch (IOException ex) {
        throw new MojoExecutionException(
            "Failed to copy proto descriptors to build output", ex);
      }

      this.descriptorOutputFile = Optional.of(outputFile);
    }
  }

  @Override
  protected SV getStateVector() {
    return new SV(this);
  }


  static final class SV implements PlanGraphNode.StateVector {
    private static final long serialVersionUID = 1;

    final Optional<File> descriptorOutputFile;

    @SuppressWarnings("synthetic-access")
    SV(CopyProtosToJar node) {
      this.descriptorOutputFile = node.descriptorOutputFile;
    }

    @SuppressWarnings("synthetic-access")
    @Override
    public PlanGraphNode<?> reconstitute(PlanContext c, JoinNodes jn) {
      CopyProtosToJar node = new CopyProtosToJar(c);
      node.descriptorOutputFile = this.descriptorOutputFile;
      return node;
    }
  }
}
