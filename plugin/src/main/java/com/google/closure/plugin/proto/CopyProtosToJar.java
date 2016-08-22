package com.google.closure.plugin.proto;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.closure.module.ClosureModule;
import com.google.closure.plugin.common.Ingredients.PathValue;
import com.google.closure.plugin.common.Ingredients.SerializedObjectIngredient;
import com.google.closure.plugin.plan.Ingredient;
import com.google.closure.plugin.plan.PlanKey;
import com.google.closure.plugin.plan.Step;
import com.google.closure.plugin.plan.StepSource;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

final class CopyProtosToJar extends Step {

  private static final String DESCRIPTORS_FILE_BASENAME =
      ClosureModule.PROTO_DESCRIPTORS_RESOURCE_PATH.substring(
          ClosureModule.PROTO_DESCRIPTORS_RESOURCE_PATH.lastIndexOf('/') + 1);

  CopyProtosToJar(
      PathValue closureOutputDirectory,
      SerializedObjectIngredient<ProtoIO> protoIO) {
    super(
        PlanKey.builder("copy-pd-to-jar").build(),
        ImmutableList.<Ingredient>of(closureOutputDirectory, protoIO),
        Sets.immutableEnumSet(StepSource.PROTO_DESCRIPTOR_SET),
        ImmutableSet.<StepSource>of());
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    PathValue closureOutputDirectory = (PathValue) inputs.get(0);
    SerializedObjectIngredient<ProtoIO> protoIO =
        ((SerializedObjectIngredient<?>) inputs.get(1))
        .asSuperType(ProtoIO.class);

    File descriptorSetFile =
        protoIO.getStoredObject().get().mainDescriptorSetFile;
    if (descriptorSetFile.exists()) {
      // Not generated if protoc is not run.
      try {
        closureOutputDirectory.value.mkdir();
        Files.copy(
            descriptorSetFile,
            new File(closureOutputDirectory.value, DESCRIPTORS_FILE_BASENAME));
      } catch (IOException ex) {
        throw new MojoExecutionException(
            "Failed to copy proto descriptors to build output", ex);
      }
    }
  }

  @Override
  public void skip(Log log) throws MojoExecutionException {
    // Done
  }

  @Override
  public ImmutableList<Step> extraSteps(Log log) {
    return ImmutableList.of();
  }

}
