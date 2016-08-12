package com.google.common.html.plugin.soy;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.html.plugin.common.GenfilesDirs;
import com.google.common.html.plugin.common.Ingredients;
import com.google.common.html.plugin.common.Ingredients.Bundle;
import com.google.common.html.plugin.common.Ingredients
    .DirScanFileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.FileIngredient;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients.PathValue;
import com.google.common.html.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.common.html.plugin.common.Ingredients.UriValue;
import com.google.common.html.plugin.common.Sources.Source;
import com.google.common.html.plugin.common.SourceFileProperty;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.PlanKey;
import com.google.common.html.plugin.plan.Step;
import com.google.common.html.plugin.plan.StepSource;
import com.google.common.html.plugin.proto.ProtoIO;
import com.google.common.io.Files;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.SoyFileSet.Builder;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.proto.SoyProtoTypeProvider;

final class BuildSoyFileSet extends Step {
  final Ingredients ingredients;
  final LifecyclePhase phase;
  final DirScanFileSetIngredient soySources;

  public BuildSoyFileSet(
      Ingredients ingredients,
      LifecyclePhase phase,
      SerializedObjectIngredient<GenfilesDirs> genfiles,
      OptionsIngredient<SoyOptions> options,
      DirScanFileSetIngredient soySources,
      SerializedObjectIngredient<ProtoIO> protoIO,
      Bundle<UriValue> projectClassPathElements,
      PathValue outputDir,
      PathValue projectBuildOutputDirectory) {
    super(
        PlanKey.builder("soy-build-file-set")
            .addInp(
                genfiles, options, soySources, protoIO,
                projectClassPathElements, outputDir,
                projectBuildOutputDirectory)
            .build(),
        ImmutableList.<Ingredient>of(
            genfiles, options, protoIO, projectClassPathElements, outputDir,
            projectBuildOutputDirectory),
        Sets.immutableEnumSet(
            StepSource.SOY_GENERATED, StepSource.SOY_SRC,
            StepSource.PROTO_DESCRIPTOR_SET),
        Sets.immutableEnumSet(StepSource.JS_GENERATED));
    this.ingredients = ingredients;
    this.phase = phase;
    this.soySources = soySources;
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    // All done.
    // We're just here to link the hash of the input files to the SoyFileSet.
  }

  @Override
  public void skip(Log log) throws MojoExecutionException {
    // All done.
  }

  @Override
  public ImmutableList<Step> extraSteps(final Log log)
  throws MojoExecutionException {
    SerializedObjectIngredient<GenfilesDirs> genfilesHolder =
        ((SerializedObjectIngredient<?>) inputs.get(0))
        .asSuperType(GenfilesDirs.class);
    OptionsIngredient<SoyOptions> optionsIng =
        ((OptionsIngredient<?>) inputs.get(1)).asSuperType(SoyOptions.class);
    SerializedObjectIngredient<ProtoIO> protoIOHolder =
        ((SerializedObjectIngredient<?>) inputs.get(2))
        .asSuperType(ProtoIO.class);
    Bundle<UriValue> protobufClassPathElements =
        ((Bundle<?>) inputs.get(3)).asSuperType(
            new Function<Ingredient, UriValue>() {
              @Override
              public UriValue apply(Ingredient input) {
                return (UriValue) input;
              }
            });
    PathValue outputDir = (PathValue) inputs.get(4);
    PathValue projectBuildOutputDirectory = (PathValue) inputs.get(5);

    final SoyOptions opts = optionsIng.getOptions();
    GenfilesDirs genfiles = genfilesHolder.getStoredObject().get();

    try {
      soySources.resolve(log);
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to find .soy sources", ex);
    }
    ImmutableList<FileIngredient> soySourceFiles = soySources.sources();
    log.debug("Found " + soySourceFiles.size() + " soy sources");

    if (Iterables.isEmpty(soySourceFiles)) {
      return ImmutableList.of();
    }


    class MakeSoyFileSetBuilder
    implements ReflectionableOperation<Void, SoyFileSet.Builder> {

      @Override
      public Builder direct(Void inp) throws MojoExecutionException {
        return opts.toSoyFileSetBuilderDirect(log);
      }

      @Override
      public Object reflect(ClassLoader cl, Object inp)
      throws MojoExecutionException, ReflectiveOperationException {
        return opts.toSoyFileSetBuilderReflective(log, cl);
      }

      @Override
      public String logDescription() {
        return "MakeSoyFileSetBuilder";
      }
    }

    final ImmutableList<Source> sources;
    {
      ImmutableList.Builder<Source> sourcesBuilder = ImmutableList.builder();
      for (FileIngredient soySource : soySourceFiles) {
        sourcesBuilder.add(soySource.source);
      }
      sources = sourcesBuilder.build();
    }

    class AddInputs
    implements ReflectionableOperation<SoyFileSet.Builder, SoyFileSet.Builder> {

      @Override
      public Builder direct(Builder sfsBuilder) throws MojoExecutionException {
        for (Source source : sources) {
          String relPath = source.relativePath.getPath();
          SoyFileKind kind =
              source.root.ps.contains(SourceFileProperty.LOAD_AS_NEEDED)
              ? SoyFileKind.DEP
              : SoyFileKind.SRC;
          try {
            CharSequence content = Files.toString(
                source.canonicalPath, Charsets.UTF_8);
            sfsBuilder.addWithKind(content, kind, relPath);
          } catch (IOException ex) {
            throw new MojoExecutionException(
                "Failed to read soy source: " + relPath, ex);
          }
        }
        return sfsBuilder;
      }

      @Override
      public Object reflect(ClassLoader cl, Object sfsBuilder)
      throws MojoExecutionException, ReflectiveOperationException {
        @SuppressWarnings("rawtypes")
        Class<? extends Enum> soyFileKindClass =
            cl.loadClass(SoyFileKind.class.getName())
            .asSubclass(Enum.class);
        Method addWithKindMethod = sfsBuilder.getClass().getMethod(
            "addWithKind", CharSequence.class, soyFileKindClass, String.class);
        for (Source source : sources) {
          String relPath = source.relativePath.getPath();
          SoyFileKind kindNotInClassLoader =
              source.root.ps.contains(SourceFileProperty.LOAD_AS_NEEDED)
              ? SoyFileKind.DEP
              : SoyFileKind.SRC;
          @SuppressWarnings("unchecked")
          Object kind = Enum.valueOf(
              soyFileKindClass, kindNotInClassLoader.name());
          try {
            CharSequence content = Files.toString(
                source.canonicalPath, Charsets.UTF_8);
            try {
              addWithKindMethod.invoke(sfsBuilder, content, kind, relPath);
            } catch (InvocationTargetException ex) {
              Throwable target = ex.getTargetException();
              if (target instanceof IOException) {
                throw (IOException) target;
              }
              throw ex;
            }
          } catch (IOException ex) {
            throw new MojoExecutionException(
                "Failed to read soy source: " + relPath, ex);
          }
        }
        return sfsBuilder;
      }

      @Override
      public String logDescription() {
        return getClass().getSimpleName();
      }
    }

    // Link the proto descriptors into the Soy type system so that Soy can
    // generate efficient JS code and so that Soy can avoid over-escaping of
    // safe-contract-type protos.
    Optional<ProtoIO> protoIOOpt = protoIOHolder.getStoredObject();
    if (!protoIOOpt.isPresent()) {
      throw new MojoExecutionException(
          "Cannot find where the proto planner put the descriptor files");
    }
    ProtoIO protoIO = protoIOOpt.get();
    final File mainDescriptorSetFile = protoIO.mainDescriptorSetFile;
    log.debug("soy using proto descriptor file " + mainDescriptorSetFile);

    class RegisterProtosWithTypeRegistry
    implements ReflectionableOperation<SoyFileSet.Builder, SoyFileSet.Builder> {

      @Override
      public Builder direct(Builder sfsBuilder) throws MojoExecutionException {
        SoyProtoTypeProvider protoTypeProvider = null;
        try {
          if (mainDescriptorSetFile.exists()) {
            protoTypeProvider = new SoyProtoTypeProvider.Builder()
                // TODO: do we need to extract descriptor set files from
                // <extract>ed dependencies and include them here?
                .addFileDescriptorSetFromFile(mainDescriptorSetFile)
                .build();
          } else {
            log.info(
                "soy skipping missing descriptor file "
                + mainDescriptorSetFile);
          }
        } catch (IOException ex) {
          throw new MojoExecutionException(
              "Soy couldn't read proto descriptors from "
              + mainDescriptorSetFile,
              ex);
        } catch (DescriptorValidationException ex) {
          throw new MojoExecutionException(
              "Malformed proto descriptors in " + mainDescriptorSetFile,
              ex);
        }
        if (protoTypeProvider != null) {
          SoyTypeRegistry typeRegistry = new SoyTypeRegistry(
              ImmutableSet.<SoyTypeProvider>of(protoTypeProvider));
          sfsBuilder.setLocalTypeRegistry(typeRegistry);
        }
        return sfsBuilder;
      }

      @Override
      public Object reflect(ClassLoader cl, Object sfsBuilder)
      throws MojoExecutionException, ReflectiveOperationException {
        Object protoTypeProvider = null;
        if (mainDescriptorSetFile.exists()) {
          Class<?> ptpBuilderClass = cl.loadClass(
              SoyProtoTypeProvider.Builder.class.getName());
          Method addFileDescriptorSetFromFile = ptpBuilderClass.getMethod(
              "addFileDescriptorSetFromFile", File.class);
          Method build = ptpBuilderClass.getMethod("build");

          Object protoTypeProviderBuilder =
              ptpBuilderClass.getConstructor()
              .newInstance();

          addFileDescriptorSetFromFile.invoke(
              protoTypeProviderBuilder, mainDescriptorSetFile);

          protoTypeProvider = build.invoke(protoTypeProviderBuilder);
        } else {
          log.info(
              "soy skipping missing descriptor file " + mainDescriptorSetFile);
        }
        if (protoTypeProvider != null) {
          Class<?> soyTypeRegistryClass = cl.loadClass(
              SoyTypeRegistry.class.getName());
          Constructor<?> ctor = soyTypeRegistryClass.getConstructor(Set.class);
          Object typeRegistry = ctor.newInstance(
              (Set<?>) Collections.singleton(protoTypeProvider));

          Method setLocalTypeRegistry = sfsBuilder.getClass().getMethod(
              "setLocalTypeRegistry", soyTypeRegistryClass);
          setLocalTypeRegistry.invoke(sfsBuilder, typeRegistry);
        }
        return sfsBuilder;
      }

      @Override
      public String logDescription() {
        return getClass().getSimpleName();
      }
    }

    FileIngredient protoDescriptors;
    try {
      protoDescriptors = ingredients.file(mainDescriptorSetFile);
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to find canonical path of proto descriptors file", ex);
    }

    class Build
    implements ReflectionableOperation<SoyFileSet.Builder, SoyFileSet> {

      @Override
      public SoyFileSet direct(Builder sfsBuilder)
      throws MojoExecutionException {
        return sfsBuilder.build();
      }

      @Override
      public Object reflect(ClassLoader cl, Object sfsBuilder)
      throws MojoExecutionException, ReflectiveOperationException {
        return sfsBuilder.getClass().getMethod("build").invoke(sfsBuilder);
      }

      @Override
      public String logDescription() {
        return getClass().getSimpleName();
      }
    }

    ReflectionableOperation<Void, SoyFileSet> makeSoyFileSet =
        ReflectionableOperation.Util.chain(
            new MakeSoyFileSetBuilder(),
            new AddInputs(),
            new RegisterProtosWithTypeRegistry(),
            new Build());

    PathValue outputJar = ingredients.pathValue(new File(
        outputDir.value, "closure-templates-" + opts.getId() + ".jar"));
    PathValue jsOutDir = ingredients.pathValue(genfiles.jsGenfiles);

    switch (phase) {
      case PROCESS_SOURCES:
      {
        SoyFileSet sfs = ReflectionableOperation.Util.direct(
            makeSoyFileSet, null);
        return ImmutableList.<Step>of(new SoyToJs(
            optionsIng, soySources, protoDescriptors, jsOutDir, sfs));
      }
      case PROCESS_CLASSES:
      {
        return ImmutableList.<Step>of(new SoyToJava(
            optionsIng, soySources, protoDescriptors,
            protobufClassPathElements, outputJar,
            projectBuildOutputDirectory, makeSoyFileSet));
      }
      default:
        throw new AssertionError(phase);
    }
  }
}
