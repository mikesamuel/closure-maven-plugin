package com.google.closure.plugin.proto;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.closure.plugin.common.GenfilesDirs;
import com.google.closure.plugin.common.Ingredients;
import com.google.closure.plugin.common.Ingredients
    .DirScanFileSetIngredient;
import com.google.closure.plugin.common.Ingredients.FileIngredient;
import com.google.closure.plugin.common.Ingredients.HashedInMemory;
import com.google.closure.plugin.common.Ingredients.PathValue;
import com.google.closure.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.closure.plugin.common.Ingredients
    .SettableFileSetIngredient;
import com.google.closure.plugin.common.ProcessRunner;
import com.google.closure.plugin.common.SourceFileProperty;
import com.google.closure.plugin.common.ToolFinder;
import com.google.closure.plugin.plan.Ingredient;
import com.google.closure.plugin.plan.Metadata;
import com.google.closure.plugin.plan.PlanKey;
import com.google.closure.plugin.plan.Step;
import com.google.closure.plugin.plan.StepSource;

/**
 * Examines configurations and finds the source roots and protoc executable.
 */
final class FindProtoFilesAndProtoc extends Step {
  private final ProcessRunner processRunner;
  private final ToolFinder<ProtoFinalOptions> protocFinder;
  private final Ingredients ingredients;
  private final SerializedObjectIngredient<ProtoIO> protoSpec;
  private final DirScanFileSetIngredient protoSources;
  private final SettableFileSetIngredient protocExec;

  FindProtoFilesAndProtoc(
      ProcessRunner processRunner,
      ToolFinder<ProtoFinalOptions> protocFinder,
      Ingredients ingredients,

      HashedInMemory<ProtoFinalOptions> options,
      HashedInMemory<GenfilesDirs> genfiles,
      SerializedObjectIngredient<ProtoPackageMap> packageMap,

      SerializedObjectIngredient<ProtoIO> protoSpec,
      DirScanFileSetIngredient protoSources,
      SettableFileSetIngredient protocExec) {
    super(
        PlanKey.builder("find-proto-files").addInp(options, packageMap).build(),
        ImmutableList.<Ingredient>of(options, genfiles, packageMap),
        ImmutableSet.<StepSource>of(
            StepSource.PROTO_SRC, StepSource.PROTO_GENERATED,
            StepSource.PROTO_PACKAGE_MAP),
        Sets.immutableEnumSet(
            StepSource.PROTOC,
            // This needs to run before things that depend on the descriptor set
            // since it schedules tasks that run on the proto descriptor set.
            StepSource.PROTO_DESCRIPTOR_SET,
            // Since this schedules RunProtoc to write JS.
            StepSource.JAVA_GENERATED,
            StepSource.JS_GENERATED));
    this.processRunner = processRunner;
    this.protocFinder = protocFinder;
    this.ingredients = ingredients;
    this.protoSpec = protoSpec;
    this.protoSources = protoSources;
    this.protocExec = protocExec;
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    HashedInMemory<ProtoFinalOptions> options =
        ((HashedInMemory<?>) inputs.get(0))
        .asSuperType(ProtoFinalOptions.class);

    ProtoFinalOptions protoOptions = options.getValue();

    setProtocExec(log);

    File mainDescriptorSetFile = protoOptions.descriptorSetFile;

    File testDescriptorSetFile = protoOptions.testDescriptorSetFile;

    protoSpec.setStoredObject(new ProtoIO(
        protoSources.spec(),
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
    setProtocExec(log);
    try {
      protoSpec.read();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to load protoc spec", ex);
    }
  }

  @Override
  public ImmutableList<Step> extraSteps(final Log log)
  throws MojoExecutionException {
    HashedInMemory<ProtoFinalOptions> optionsIng =
        ((HashedInMemory<?>) inputs.get(0))
        .asSuperType(ProtoFinalOptions.class);
    HashedInMemory<GenfilesDirs> genfiles = ((HashedInMemory<?>) inputs.get(1))
        .asSuperType(GenfilesDirs.class);
    SerializedObjectIngredient<ProtoPackageMap> packageMapIng =
        ((SerializedObjectIngredient<?>) inputs.get(2))
        .asSuperType(ProtoPackageMap.class);

    final ProtoFinalOptions options = optionsIng.getValue();
    final ProtoPackageMap packageMap = packageMapIng.getStoredObject().get();
    ProtoIO protocSpecValue = protoSpec.getStoredObject().get();

    PathValue mainDescriptorSet = ingredients.pathValue(
        protocSpecValue.mainDescriptorSetFile);
    PathValue testDescriptorSet = ingredients.pathValue(
        protocSpecValue.testDescriptorSetFile);

    GenfilesDirs gf = genfiles.getValue();

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
    Predicate<FileIngredient> inJavaOnlySet = new Predicate<FileIngredient>() {
      @Override
      public boolean apply(FileIngredient inp) {
        File protoFile = inp.source.canonicalPath;
        Metadata<Optional<String>> packageMd = packageMap.protoPackages.get(
            protoFile);
        if (packageMd == null) {
          log.warn("Missing package metadata for " + protoFile);
          return false;  // Leave in all set.
        }
        Optional<String> packageName = packageMd.metadata;
        return packageName.isPresent()
            && options.javaOnly.contains(packageName.get());
      }
    };
    Predicate<FileIngredient> inJsOnlySet = new Predicate<FileIngredient>() {
      @Override
      public boolean apply(FileIngredient inp) {
        File protoFile = inp.source.canonicalPath;
        Metadata<Optional<String>> packageMd = packageMap.protoPackages.get(
            protoFile);
        if (packageMd == null) {
          return false;  // Leave in all set.
        }
        Optional<String> packageName = packageMd.metadata;
        return packageName.isPresent()
            && options.jsOnly.contains(packageName.get());
      }
    };

    ImmutableList.Builder<Step> steps = ImmutableList.builder();

    RunProtoc.RootSet[] roots = RunProtoc.RootSet.values();
    RunProtoc.LangSet[] langs = RunProtoc.LangSet.values();

    for (RunProtoc.RootSet root : roots) {
      for (RunProtoc.LangSet lang : langs) {
        Predicate<FileIngredient> rootFilter = null;
        PathValue descriptorSet = null;
        switch (root) {
          case MAIN:
            rootFilter = Predicates.not(isTestOnly);
            descriptorSet = mainDescriptorSet;
            break;
          case TEST:
            rootFilter = isTestOnly;
            descriptorSet = testDescriptorSet;
            break;
        }
        assert rootFilter != null && descriptorSet != null;

        Predicate<FileIngredient> langFilter = null;
        switch (lang) {
          case ALL:
            @SuppressWarnings("unchecked")
            Predicate<FileIngredient> notSpecialOrDep = Predicates.not(
                Predicates.or(isDep, inJavaOnlySet, inJsOnlySet));
            langFilter = notSpecialOrDep;
            break;
          case JAVA_ONLY:
            langFilter = inJavaOnlySet;
            break;
          case JS_ONLY:
            langFilter = inJsOnlySet;
            break;
        }
        assert langFilter != null;

        Predicate<FileIngredient> inputFilter = Predicates.and(
            rootFilter,
            langFilter);

        Iterable<FileIngredient> sources =
            Iterables.filter(protoSources.sources(), inputFilter);

        // Compile the main files separately from the test files since protoc
        // has a single output directory.
        if (!Iterables.isEmpty(sources)) {
          steps.add(new RunProtoc(
              processRunner, root, lang,
              optionsIng, protoSources, protocExec,
              ingredients.bundle(sources),
              ingredients.pathValue(gf.javaGenfiles),
              ingredients.pathValue(gf.jsGenfiles),
              descriptorSet));
        }
      }
    }

    return steps.build();
  }

  private void setProtocExec(Log log) {
    HashedInMemory<ProtoFinalOptions> options =
        ((HashedInMemory<?>) inputs.get(0))
        .asSuperType(ProtoFinalOptions.class);
    protocFinder.find(log, options.getValue(), ingredients, protocExec);
  }
}
