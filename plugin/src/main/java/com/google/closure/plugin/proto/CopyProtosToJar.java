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

  private Optional<File> descriptorOutputFile = Optional.absent();

  CopyProtosToJar(PlanContext context) {
    super(context);
  }

  static final class SV implements PlanGraphNode.StateVector {
    private static final long serialVersionUID = 1;

    @Override
    public PlanGraphNode<?> reconstitute(PlanContext c, JoinNodes jn) {
      return new CopyProtosToJar(c);
    }
  }


  @Override
  protected boolean hasChangedInputs() throws IOException {
    return true;
  }

  @Override
  protected void processInputs() throws IOException, MojoExecutionException {
    File descriptorSetFile = context.protoIO.mainDescriptorSetFile.get();
    if (descriptorSetFile.exists()) {
      File outputFile = new File(
          context.closureOutputDirectory,
          DESCRIPTORS_FILE_BASENAME);
      // Not generated if protoc is not run.
      try {
        context.closureOutputDirectory.mkdirs();
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
  protected
  Optional<ImmutableList<PlanGraphNode<?>>> rebuildFollowersList(JoinNodes jn) {
    return Optional.absent();
  }

  @Override
  protected void markOutputs() {
    if (descriptorOutputFile.isPresent()) {
      context.buildContext.refresh(descriptorOutputFile.get());
    }
  }

  @Override
  protected SV getStateVector() {
    return new SV();
  }
}
