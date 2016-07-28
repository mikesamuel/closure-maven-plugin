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
import com.google.common.html.plugin.Sources.Source;
import com.google.common.html.plugin.common.GenfilesDirs;
import com.google.common.html.plugin.common.Ingredients;
import com.google.common.html.plugin.common.Ingredients
    .DirScanFileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.FileIngredient;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients.PathValue;
import com.google.common.html.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.PlanKey;
import com.google.common.html.plugin.plan.Step;
import com.google.common.html.plugin.plan.StepSource;
import com.google.common.html.plugin.proto.ProtoIO;
import com.google.common.io.Files;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.proto.SoyProtoTypeProvider;

final class BuildSoyFileSet extends Step {
  final Ingredients ingredients;

  public BuildSoyFileSet(
      Ingredients ingredients,
      SerializedObjectIngredient<GenfilesDirs> genfiles,
      OptionsIngredient<SoyOptions> options,
      DirScanFileSetIngredient soySources,
      SerializedObjectIngredient<ProtoIO> protoIO,
      PathValue outputDir) {
    super(
        PlanKey.builder("soy-build-file-set")
            .addInp(genfiles, options, soySources, protoIO, outputDir)
            .build(),
        ImmutableList.<Ingredient>of(
            genfiles, options, soySources, protoIO, outputDir),
        Sets.immutableEnumSet(
            StepSource.SOY_GENERATED, StepSource.SOY_SRC,
            StepSource.PROTO_DESCRIPTOR_SET),
        Sets.immutableEnumSet(StepSource.JS_GENERATED));
    this.ingredients = ingredients;
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
  public ImmutableList<Step> extraSteps(Log log) throws MojoExecutionException {
    SerializedObjectIngredient<GenfilesDirs> genfilesHolder =
        ((SerializedObjectIngredient<?>) inputs.get(0))
        .asSuperType(GenfilesDirs.class);
    OptionsIngredient<SoyOptions> optionsIng =
        ((OptionsIngredient<?>) inputs.get(1)).asSuperType(SoyOptions.class);
    DirScanFileSetIngredient soySources =
        (DirScanFileSetIngredient) inputs.get(2);
    SerializedObjectIngredient<ProtoIO> protoIOHolder =
        ((SerializedObjectIngredient<?>) inputs.get(3))
        .asSuperType(ProtoIO.class);
    PathValue outputDir = (PathValue) inputs.get(4);

    SoyOptions opts = optionsIng.getOptions();
    GenfilesDirs genfiles = genfilesHolder.getStoredObject().get();

    try {
      soySources.resolve(log);
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to find .soy sources", ex);
    }
    ImmutableList<FileIngredient> soySourceFiles = soySources.mainSources();
    log.debug("Found " + soySourceFiles.size() + " soy sources");

    if (Iterables.isEmpty(soySourceFiles)) {
      return ImmutableList.of();
    }

    SoyFileSet.Builder sfsBuilder = opts.toSoyFileSetBuilder(log);

    ImmutableList.Builder<Source> sources = ImmutableList.builder();
    for (FileIngredient soySource : soySourceFiles) {
      File relPath = soySource.source.relativePath;
      try {
        sfsBuilder.add(
            Files.toString(soySource.source.canonicalPath, Charsets.UTF_8),
            relPath.getPath());
      } catch (IOException ex) {
        throw new MojoExecutionException(
            "Failed to read soy source: " + relPath, ex);
      }
      sources.add(soySource.source);
    }

    // Link the proto descriptors into the Soy type system so that Soy can
    // generate efficient JS code and so that Soy can avoid over-escaping of
    // safe-contract-type protos.
    Optional<ProtoIO> protoIO = protoIOHolder.getStoredObject();
    if (!protoIO.isPresent()) {
      throw new MojoExecutionException(
          "Cannot find where the proto planner put the descriptor files");
    }
    SoyProtoTypeProvider protoTypeProvider = null;
    File mainDescriptorSetFile = protoIO.get().mainDescriptorSetFile;
    log.debug("soy using proto descriptor file " + mainDescriptorSetFile);
    try {
      if (mainDescriptorSetFile.exists()) {
        protoTypeProvider = new SoyProtoTypeProvider.Builder()
            // TODO: do we need to extract descriptor set files from
            // <extract>ed dependencies and include them here?
            .addFileDescriptorSetFromFile(mainDescriptorSetFile)
            .build();
      } else {
        log.info(
            "soy skipping missing descriptor file " + mainDescriptorSetFile);
      }
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Soy couldn't read proto descriptors from " + mainDescriptorSetFile,
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
        new SoyToJava(optionsIng, soySources, protoDescriptors, outputJar, sfs),
        new SoyToJs(optionsIng, soySources, protoDescriptors, jsOutDir, sfs)
        );
  }
}
