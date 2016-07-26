package com.google.common.html.plugin.proto;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.html.plugin.Sources;
import com.google.common.html.plugin.common.GenfilesDirs;
import com.google.common.html.plugin.common.Ingredients;
import com.google.common.html.plugin.common.Ingredients.DirScanFileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.FileIngredient;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients.SerializedObjectIngredient;
import com.google.common.html.plugin.common.Ingredients.SettableFileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.StringValue;
import com.google.common.html.plugin.common.ProcessRunner;
import com.google.common.html.plugin.common.ToolFinder;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.Step;
import com.google.common.html.plugin.plan.StepSource;

/**
 * Examines configurations and finds the source roots and protoc executable.
 */
final class FindProtoFilesAndProtoc extends Step {
  private final ProcessRunner processRunner;
  private final ToolFinder<ProtoOptions> protocFinder;
  private final Ingredients ingredients;
  private final SerializedObjectIngredient<ProtoIO> protoSpec;
  private final SettableFileSetIngredient protocExec;

  FindProtoFilesAndProtoc(
      ProcessRunner processRunner,
      ToolFinder<ProtoOptions> protocFinder,
      Ingredients ingredients,

      OptionsIngredient<ProtoOptions> options,
      SerializedObjectIngredient<GenfilesDirs> genfiles,
      StringValue defaultProtoSourcePath,
      StringValue defaultProtoTestSourcePath,
      StringValue defaultMainDescriptorFilePath,
      StringValue defaultTestDescriptorFilePath,

      SerializedObjectIngredient<ProtoIO> protoSpec,
      SettableFileSetIngredient protocExec) {
    super(
        "find-proto-files:" + options.key,
        ImmutableList.<Ingredient>of(
            options, genfiles,
            defaultProtoSourcePath, defaultProtoTestSourcePath,
            defaultMainDescriptorFilePath, defaultTestDescriptorFilePath),
        ImmutableSet.<StepSource>of(
            StepSource.PROTO_SRC, StepSource.PROTO_GENERATED),
        Sets.immutableEnumSet(StepSource.PROTOC));
    this.processRunner = processRunner;
    this.protocFinder = protocFinder;
    this.ingredients = ingredients;
    this.protoSpec = protoSpec;
    this.protocExec = protocExec;
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    OptionsIngredient<ProtoOptions> options =
        ((OptionsIngredient<?>) inputs.get(0)).asSuperType(ProtoOptions.class);
    SerializedObjectIngredient<GenfilesDirs> genfiles =
        ((SerializedObjectIngredient<?>) inputs.get(1))
        .asSuperType(GenfilesDirs.class);
    StringValue defaultProtoSourcePath = (StringValue) inputs.get(2);
    StringValue defaultProtoTestSourcePath = (StringValue) inputs.get(3);
    StringValue defaultMainDescriptorFilePath = (StringValue) inputs.get(4);
    StringValue defaultTestDescriptorFilePath = (StringValue) inputs.get(5);

    GenfilesDirs gf = genfiles.getStoredObject().get();

    ProtoOptions protoOptions = options.getOptions();

    ImmutableSet.Builder<File> mainSources = ImmutableSet.builder();
    if (protoOptions.source != null) {
      mainSources.addAll(ImmutableList.copyOf(protoOptions.source));
    } else {
      mainSources.add(new File(defaultProtoSourcePath.value));
    }
    mainSources.add(gf.getGeneratedSourceDirectoryForExtension("proto", false));

    ImmutableSet.Builder<File> testSources = ImmutableSet.builder();
    if (protoOptions.testSource != null) {
      testSources.addAll(ImmutableList.copyOf(protoOptions.testSource));
    } else {
      testSources.add(new File(defaultProtoTestSourcePath.value));
    }
    testSources.add(gf.getGeneratedSourceDirectoryForExtension("proto", true));

    setProtocExec();

    File mainDescriptorSetFile =
        protoOptions.descriptorSetFile != null
        ? protoOptions.descriptorSetFile
        : new File(defaultMainDescriptorFilePath.value);

    File testDescriptorSetFile =
        protoOptions.testDescriptorSetFile != null
        ? protoOptions.testDescriptorSetFile
        : new File(defaultTestDescriptorFilePath.value);

    protoSpec.setStoredObject(new ProtoIO(
        ImmutableList.copyOf(mainSources.build()),
        ImmutableList.copyOf(testSources.build()),
        mainDescriptorSetFile,
        testDescriptorSetFile
        ));
    try {
      protoSpec.write();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to store protoc spec", ex);
    }
  }

  @Override
  public void skip(Log log) throws MojoExecutionException {
    setProtocExec();
    try {
      protoSpec.read();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to load protoc spec", ex);
    }
  }

  @Override
  public ImmutableList<Step> extraSteps(Log log) throws MojoExecutionException {
    OptionsIngredient<ProtoOptions> options =
        ((OptionsIngredient<?>) inputs.get(0)).asSuperType(ProtoOptions.class);
    SerializedObjectIngredient<GenfilesDirs> genfiles =
        ((SerializedObjectIngredient<?>) inputs.get(1))
        .asSuperType(GenfilesDirs.class);

    ProtoIO protocSpecValue = protoSpec.getStoredObject().get();

    DirScanFileSetIngredient protoSources =
        ingredients.fileset(new Sources.Finder(".proto")
            .mainRoots(protocSpecValue.mainSourceRoots)
            .testRoots(protocSpecValue.testSourceRoots));
    try {
      protoSources.resolve(log);
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to find .proto sources", ex);
    }

    FileIngredient mainDescriptorSet;
    FileIngredient testDescriptorSet;
    try {
      mainDescriptorSet = ingredients.file(
          protocSpecValue.mainDescriptorSetFile);
      testDescriptorSet = ingredients.file(
          protocSpecValue.testDescriptorSetFile);
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to find position for descriptor set output files", ex);
    }

    GenfilesDirs gf = genfiles.getStoredObject().get();

    // Compile the main files separately from the test files since protoc
    // has a single output directory.
    RunProtoc main = new RunProtoc(
        processRunner,
        RunProtoc.RootSet.MAIN,
        options, protoSources, protocExec,
        protoSources.mainSources(),
        ingredients.stringValue(gf.javaGenfiles.getPath()),
        ingredients.stringValue(gf.jsGenfiles.getPath()),
        mainDescriptorSet);
    RunProtoc test = new RunProtoc(
        processRunner,
        RunProtoc.RootSet.TEST,
        options, protoSources, protocExec,
        protoSources.testSources(),
        ingredients.stringValue(gf.javaTestGenfiles.getPath()),
        ingredients.stringValue(gf.jsTestGenfiles.getPath()),
        testDescriptorSet);

    return ImmutableList.<Step>of(main, test);
  }

  private void setProtocExec() {
    System.err.println();
    OptionsIngredient<ProtoOptions> options =
        ((OptionsIngredient<?>) inputs.get(0)).asSuperType(ProtoOptions.class);
    protocFinder.find(options.getOptions(), ingredients, protocExec);
  }

}
