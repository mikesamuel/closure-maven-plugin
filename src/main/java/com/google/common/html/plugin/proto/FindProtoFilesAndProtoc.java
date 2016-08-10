package com.google.common.html.plugin.proto;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.html.plugin.common.DirectoryScannerSpec;
import com.google.common.html.plugin.common.GenfilesDirs;
import com.google.common.html.plugin.common.Ingredients;
import com.google.common.html.plugin.common.Ingredients
    .DirScanFileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.FileIngredient;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients.PathValue;
import com.google.common.html.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.common.html.plugin.common.Ingredients
    .SettableFileSetIngredient;
import com.google.common.html.plugin.common.ProcessRunner;
import com.google.common.html.plugin.common.SourceFileProperty;
import com.google.common.html.plugin.common.ToolFinder;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.PlanKey;
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
      PathValue defaultProtoSourcePath,
      PathValue defaultProtoTestSourcePath,
      PathValue defaultMainDescriptorFilePath,
      PathValue defaultTestDescriptorFilePath,

      SerializedObjectIngredient<ProtoIO> protoSpec,
      SettableFileSetIngredient protocExec) {
    super(
        PlanKey.builder("find-proto-files").addInp(options).build(),
        ImmutableList.<Ingredient>of(
            options, genfiles,
            defaultProtoSourcePath, defaultProtoTestSourcePath,
            defaultMainDescriptorFilePath, defaultTestDescriptorFilePath),
        ImmutableSet.<StepSource>of(
            StepSource.PROTO_SRC, StepSource.PROTO_GENERATED),
        Sets.immutableEnumSet(
            StepSource.PROTOC,
            // This needs to run before things that depend on the descriptor set
            // since it schedules tasks that run on the proto descriptor set.
            StepSource.PROTO_DESCRIPTOR_SET));
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
    PathValue defaultProtoSourcePath = (PathValue) inputs.get(2);
    PathValue defaultProtoTestSourcePath = (PathValue) inputs.get(3);
    PathValue defaultMainDescriptorFilePath = (PathValue) inputs.get(4);
    PathValue defaultTestDescriptorFilePath = (PathValue) inputs.get(5);

    GenfilesDirs gf = genfiles.getStoredObject().get();

    ProtoOptions protoOptions = options.getOptions();

    DirectoryScannerSpec protoSources = protoOptions.toDirectoryScannerSpec(
        defaultProtoSourcePath.value,
        defaultProtoTestSourcePath.value,
        gf);

    setProtocExec();

    File mainDescriptorSetFile =
        protoOptions.descriptorSetFile != null
        ? protoOptions.descriptorSetFile
        : defaultMainDescriptorFilePath.value;

    File testDescriptorSetFile =
        protoOptions.testDescriptorSetFile != null
        ? protoOptions.testDescriptorSetFile
        : defaultTestDescriptorFilePath.value;

    protoSpec.setStoredObject(new ProtoIO(
        protoSources,
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
        ingredients.fileset(protocSpecValue.protoSources);
    try {
      protoSources.resolve(log);
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to find .proto sources", ex);
    }

    PathValue mainDescriptorSet = ingredients.pathValue(
        protocSpecValue.mainDescriptorSetFile);
    PathValue testDescriptorSet = ingredients.pathValue(
        protocSpecValue.testDescriptorSetFile);

    GenfilesDirs gf = genfiles.getStoredObject().get();

    Predicate<FileIngredient> isTestOnly = new Predicate<FileIngredient>() {
      @Override
      public boolean apply(FileIngredient ing) {
        return ing.source.root.ps.contains(SourceFileProperty.TEST_ONLY);
      }
    };
    Predicate<FileIngredient> isDep = new Predicate<FileIngredient>() {
      @Override
      public boolean apply(FileIngredient ing) {
        return ing.source.root.ps.contains(SourceFileProperty.LOAD_AS_NEEDED);
      }
    };
    Predicate<FileIngredient> isMainSource = Predicates.not(Predicates.or(
        isTestOnly, isDep));
    Predicate<FileIngredient> isTestSource = Predicates.and(
        isTestOnly, Predicates.not(isDep));

    Iterable<FileIngredient> mainSources =
        Iterables.filter(protoSources.sources(), isMainSource);
    Iterable<FileIngredient> testSources =
        Iterables.filter(protoSources.sources(), isTestSource);

    // Compile the main files separately from the test files since protoc
    // has a single output directory.
    RunProtoc main = new RunProtoc(
        processRunner,
        RunProtoc.RootSet.MAIN,
        options, protoSources, protocExec,
        ingredients.bundle(mainSources),
        ingredients.pathValue(gf.javaGenfiles),
        ingredients.pathValue(gf.jsGenfiles),
        mainDescriptorSet);
    RunProtoc test = new RunProtoc(
        processRunner,
        RunProtoc.RootSet.TEST,
        options, protoSources, protocExec,
        ingredients.bundle(testSources),
        ingredients.pathValue(gf.javaTestGenfiles),
        ingredients.pathValue(gf.jsTestGenfiles),
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
