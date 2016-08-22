package com.google.closure.plugin.proto;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Preconditions;
import com.google.closure.plugin.common.CommonPlanner;
import com.google.closure.plugin.common.Ingredients.DirScanFileSetIngredient;
import com.google.closure.plugin.common.Ingredients.HashedInMemory;
import com.google.closure.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.closure.plugin.common.Ingredients
    .SettableFileSetIngredient;
import com.google.closure.plugin.common.OptionsUtils;
import com.google.closure.plugin.common.ToolFinder;

/**
 * Adds steps that feed .proto files to protoc.
 */
public final class ProtoPlanner {

  private final CommonPlanner planner;
  private final ToolFinder<ProtoFinalOptions> protocFinder;
  private File defaultProtoSource;
  private File defaultProtoTestSource;
  private File defaultMainDescriptorFile;
  private File defaultTestDescriptorFile;
  private final SerializedObjectIngredient<ProtoIO> protoIO;

  /** */
  public ProtoPlanner(
      CommonPlanner planner, ToolFinder<ProtoFinalOptions> protocFinder)
  throws IOException {
    this.planner = planner;
    this.protocFinder = protocFinder;

    this.protoIO = planner.ingredients.serializedObject(
        "protoc-files.ser", ProtoIO.class);
  }


  /**
   * Gets info about protobuf compiler inputs and outputs derived from the
   * proto options and file-system.
   */
  public SerializedObjectIngredient<ProtoIO> getProtoIO() {
    return protoIO;
  }

  /** Sets the default source root for proto files used in sources. */
  public ProtoPlanner defaultProtoSource(File dir) {
    this.defaultProtoSource = dir;
    return this;
  }

  /** Sets the default source root for proto files used in tests. */
  public ProtoPlanner defaultProtoTestSource(File dir) {
    this.defaultProtoTestSource = dir;
    return this;
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
  public void plan(ProtoOptions opts) throws MojoExecutionException {
    Preconditions.checkNotNull(defaultProtoSource);
    Preconditions.checkNotNull(defaultProtoTestSource);
    Preconditions.checkNotNull(defaultMainDescriptorFile);
    Preconditions.checkNotNull(defaultTestDescriptorFile);
    Preconditions.checkNotNull(opts);

    ProtoFinalOptions protoOptions = OptionsUtils.prepareOne(opts).freeze(
        defaultProtoSource, defaultProtoTestSource,
        planner.genfiles.getValue(),
        defaultMainDescriptorFile, defaultTestDescriptorFile);

    HashedInMemory<ProtoFinalOptions> protoOptionsIng =
        planner.ingredients.hashedInMemory(
            ProtoFinalOptions.class, protoOptions);

    SettableFileSetIngredient protocExec =
        planner.ingredients.namedFileSet("protocExec");

    SerializedObjectIngredient<ProtoPackageMap> protoPackageMap;
    try {
      protoPackageMap = planner.ingredients.serializedObject(
          "proto-package-map.ser", ProtoPackageMap.class);
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to locate intermediate object", ex);
    }

    DirScanFileSetIngredient protoSources =
        planner.ingredients.fileset(protoOptions.sources);

    planner.addStep(new GenerateProtoPackageMap(
        protoSources, protoPackageMap));

    planner.addStep(new FindProtoFilesAndProtoc(
        planner.processRunner, protocFinder, planner.ingredients,
        protoOptionsIng, planner.genfiles, protoPackageMap,
        protoIO, protoSources, protocExec));

    planner.addStep(new CopyProtosToJar(
        planner.closureOutputDirectory,
        protoIO));
  }

}
