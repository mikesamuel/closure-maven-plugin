package com.google.common.html.plugin.proto;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.html.plugin.Sources;
import com.google.common.html.plugin.common.GenfilesDirs;
import com.google.common.html.plugin.common.Ingredients;
import com.google.common.html.plugin.common.Ingredients.DirScanFileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.FileIngredient;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients.SerializedObjectIngredient;
import com.google.common.html.plugin.common.Ingredients.SettableFileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.StringValue;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.Step;

final class FindProtoFilesAndProtoc extends Step {
  private final Function<ProtoOptions, File> protocExecSupplier;
  private final Ingredients ingredients;

  FindProtoFilesAndProtoc(
      Function<ProtoOptions, File> protocExecSupplier,
      Ingredients ingredients,

      OptionsIngredient<ProtoOptions> options,
      SerializedObjectIngredient<GenfilesDirs> genfiles,
      StringValue defaultProtoSourcePath,
      StringValue defaultProtoTestSourcePath,
      StringValue defaultMainDescriptorFilePath,
      StringValue defaultTestDescriptorFilePath,

      SerializedObjectIngredient<ProtocSpec> protoSpec,
      SettableFileSetIngredient protocExec) {
    super(
        "find-proto-files:" + options.key,
        ImmutableList.<Ingredient>of(
            options, genfiles,
            defaultProtoSourcePath, defaultProtoTestSourcePath,
            defaultMainDescriptorFilePath, defaultTestDescriptorFilePath),
        ImmutableList.<Ingredient>of(protoSpec, protocExec));
    this.protocExecSupplier = protocExecSupplier;
    this.ingredients = ingredients;
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    OptionsIngredient<ProtoOptions> options =
        ((OptionsIngredient<?>) inputs.get(0)).asSuperType(ProtoOptions.class);
    StringValue defaultProtoSourcePath = (StringValue) inputs.get(2);
    StringValue defaultProtoTestSourcePath = (StringValue) inputs.get(3);
    StringValue defaultMainDescriptorFilePath = (StringValue) inputs.get(4);
    StringValue defaultTestDescriptorFilePath = (StringValue) inputs.get(5);

    SerializedObjectIngredient<ProtocSpec> protoSpec =
        ((SerializedObjectIngredient<?>) outputs.get(0))
        .asSuperType(ProtocSpec.class);

    ProtoOptions protoOptions = options.getOptions();

    setProtocExec();

    File mainDescriptorSetFile =
        protoOptions.descriptorSetFile != null
        ? protoOptions.descriptorSetFile
        : new File(defaultMainDescriptorFilePath.value);

    File testDescriptorSetFile =
        protoOptions.testDescriptorSetFile != null
        ? protoOptions.testDescriptorSetFile
        : new File(defaultTestDescriptorFilePath.value);

    protoSpec.setStoredObject(new ProtocSpec(
        protoOptions.source != null
        ? ImmutableList.copyOf(protoOptions.source)
        : ImmutableList.of(new File(defaultProtoSourcePath.value)),

        protoOptions.testSource != null
        ? ImmutableList.copyOf(protoOptions.testSource)
        : ImmutableList.of(new File(defaultProtoTestSourcePath.value)),

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
    SerializedObjectIngredient<ProtocSpec> protoSpec =
        ((SerializedObjectIngredient<?>) outputs.get(0))
        .asSuperType(ProtocSpec.class);
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

    SerializedObjectIngredient<ProtocSpec> protoSpec =
        ((SerializedObjectIngredient<?>) outputs.get(0))
        .asSuperType(ProtocSpec.class);

    ProtocSpec protocSpecValue = protoSpec.getStoredObject().get();

    SettableFileSetIngredient protocExecs =
        (SettableFileSetIngredient) outputs.get(1);

    FileIngredient protocExec = protocExecs.mainSources().get().get(0);

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
        RunProtoc.RootSet.MAIN,
        options, protoSources, protocExec,
        protoSources.mainSources().get(),
        ingredients.stringValue(gf.javaGenfiles.getPath()),
        ingredients.stringValue(gf.jsGenfiles.getPath()),
        mainDescriptorSet);
    RunProtoc test = new RunProtoc(
        RunProtoc.RootSet.TEST,
        options, protoSources, protocExec,
        protoSources.testSources().get(),
        ingredients.stringValue(gf.javaTestGenfiles.getPath()),
        ingredients.stringValue(gf.jsTestGenfiles.getPath()),
        testDescriptorSet);

    return ImmutableList.<Step>of(main, test);
  }

  private void setProtocExec() throws MojoExecutionException {
    OptionsIngredient<ProtoOptions> options =
        ((OptionsIngredient<?>) inputs.get(0)).asSuperType(ProtoOptions.class);

    SettableFileSetIngredient protocExec =
        (SettableFileSetIngredient) outputs.get(1);
    try {
      protocExec.setFiles(
          ImmutableList.of(ingredients.file(
              protocExecSupplier.apply(options.getOptions()))),
          ImmutableList.<FileIngredient>of());
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to find .proto roots", ex);
    }
  }

}
