package com.google.common.html.plugin.proto;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Preconditions;
import com.google.common.html.plugin.common.CommonPlanner;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.common.html.plugin.common.Ingredients
    .SettableFileSetIngredient;
import com.google.common.html.plugin.common.OptionsUtils;
import com.google.common.html.plugin.common.ToolFinder;

/**
 * Adds steps that feed .proto files to protoc.
 */
public final class ProtoPlanner {

  private final CommonPlanner planner;
  private final ToolFinder<ProtoOptions> protocFinder;
  private File defaultProtoSource;
  private File defaultProtoTestSource;
  private File defaultMainDescriptorFile;
  private File defaultTestDescriptorFile;
  private final File protoDir;
  private final SerializedObjectIngredient<ProtoIO> protoIO;

  /** */
  public ProtoPlanner(
      CommonPlanner planner, ToolFinder<ProtoOptions> protocFinder)
  throws IOException {
    this.planner = planner;
    this.protocFinder = protocFinder;

    this.protoDir = new File(planner.outputDir, "proto");
    this.protoIO = planner.ingredients.serializedObject(
        new File(protoDir, "protoc-files.ser"),
        ProtoIO.class);
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

    ProtoOptions protoOptions = OptionsUtils.prepareOne(opts);

    OptionsIngredient<ProtoOptions> protoOptionsIng =
        planner.ingredients.options(ProtoOptions.class, protoOptions);

    SettableFileSetIngredient protocExec =
        planner.ingredients.namedFileSet("protocExec");

    planner.addStep(new FindProtoFilesAndProtoc(
        planner.processRunner, protocFinder, planner.ingredients,
        protoOptionsIng,
        planner.genfiles,
        planner.ingredients.pathValue(defaultProtoSource),
        planner.ingredients.pathValue(defaultProtoTestSource),
        planner.ingredients.pathValue(defaultMainDescriptorFile),
        planner.ingredients.pathValue(defaultTestDescriptorFile),
        protoIO, protocExec));
  }

}
