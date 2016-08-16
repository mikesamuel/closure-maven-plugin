package com.google.common.html.plugin.soy;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.html.plugin.common.GenfilesDirs;
import com.google.common.html.plugin.common.Ingredients;
import com.google.common.html.plugin.common.Ingredients
    .DirScanFileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.FileIngredient;
import com.google.common.html.plugin.common.Ingredients.HashedInMemory;
import com.google.common.html.plugin.common.Ingredients.PathValue;
import com.google.common.html.plugin.common.Ingredients
    .SerializedObjectIngredient;
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
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.proto.SoyProtoTypeProvider;

final class BuildSoyFileSet extends Step {
  final Ingredients ingredients;
  final DirScanFileSetIngredient soySources;

  public BuildSoyFileSet(
      Ingredients ingredients,
      SerializedObjectIngredient<GenfilesDirs> genfiles,
      HashedInMemory<SoyOptions> options,
      DirScanFileSetIngredient soySources,
      SerializedObjectIngredient<ProtoIO> protoIO,
      PathValue outputDir,
      PathValue projectBuildOutputDirectory) {
    super(
        PlanKey.builder("soy-build-file-set")
            .addInp(
                genfiles, options, soySources, protoIO,
                outputDir, projectBuildOutputDirectory)
            .build(),
        ImmutableList.<Ingredient>of(
            genfiles, options, protoIO, outputDir, projectBuildOutputDirectory),
        Sets.immutableEnumSet(
            StepSource.SOY_GENERATED, StepSource.SOY_SRC,
            StepSource.PROTO_DESCRIPTOR_SET),
        Sets.immutableEnumSet(StepSource.JS_GENERATED));
    this.ingredients = ingredients;
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
    HashedInMemory<SoyOptions> optionsIng =
        ((HashedInMemory<?>) inputs.get(1)).asSuperType(SoyOptions.class);
    SerializedObjectIngredient<ProtoIO> protoIOHolder =
        ((SerializedObjectIngredient<?>) inputs.get(2))
        .asSuperType(ProtoIO.class);
    PathValue outputDir = (PathValue) inputs.get(3);
    PathValue projectBuildOutputDirectory = (PathValue) inputs.get(4);

    final SoyOptions opts = optionsIng.getValue();
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



    SoyFileSet.Builder sfsBuilder = opts.toSoyFileSetBuilder(log);

    final ImmutableList<Source> sources;
    {
      ImmutableList.Builder<Source> sourcesBuilder = ImmutableList.builder();
      for (FileIngredient soySource : soySourceFiles) {
        sourcesBuilder.add(soySource.source);
      }
      sources = sourcesBuilder.build();
    }

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

    FileIngredient protoDescriptors;
    try {
      protoDescriptors = ingredients.file(mainDescriptorSetFile);
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to find canonical path of proto descriptors file", ex);
    }

    SoyFileSet sfs = sfsBuilder.build();

    PathValue outputJar = ingredients.pathValue(new File(
        outputDir.value, "closure-templates-" + opts.getId() + ".jar"));
    PathValue jsOutDir = ingredients.pathValue(genfiles.jsGenfiles);

    return ImmutableList.<Step>of(
        new SoyToJs(
            optionsIng, soySources, protoDescriptors, jsOutDir, sfs),
        new SoyToJava(
            optionsIng, soySources, protoDescriptors,
            outputJar, projectBuildOutputDirectory, sfs));
  }
}
