package com.google.closure.plugin.proto;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.closure.plugin.common.CStyleLexer;
import com.google.closure.plugin.common.Ingredients
    .DirScanFileSetIngredient;
import com.google.closure.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.plan.FileMetadataMapBuilder;
import com.google.closure.plugin.plan.Ingredient;
import com.google.closure.plugin.plan.PlanKey;
import com.google.closure.plugin.plan.Step;
import com.google.closure.plugin.plan.StepSource;

final class GenerateProtoPackageMap extends Step {
  final SerializedObjectIngredient<ProtoPackageMap> protoPackageMapIng;

  GenerateProtoPackageMap(
      DirScanFileSetIngredient protoSources,
      SerializedObjectIngredient<ProtoPackageMap> protoPackageMapIng) {
    super(
        PlanKey.builder("generate-proto-package-map")
            .addInp(protoSources)
            .build(),
        ImmutableList.<Ingredient>of(protoSources),
        Sets.immutableEnumSet(
            StepSource.PROTO_GENERATED, StepSource.PROTO_SRC),
        Sets.immutableEnumSet(
            StepSource.PROTO_PACKAGE_MAP));
    this.protoPackageMapIng = protoPackageMapIng;
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    DirScanFileSetIngredient protoSources =
        (DirScanFileSetIngredient) inputs.get(0);

    try {
      protoPackageMapIng.read();
    } catch (@SuppressWarnings("unused") FileNotFoundException ex) {
      // Okay.  Regenerate below.
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to read proto package map", ex);
    }
    ProtoPackageMap oldMap = protoPackageMapIng.getStoredObject().isPresent()
        ? protoPackageMapIng.getStoredObject().get()
        : ProtoPackageMap.EMPTY;

    ProtoPackageMap protoPackageMap;

    try {
      protoPackageMap = new ProtoPackageMap(
          FileMetadataMapBuilder.updateFromIngredients(
              oldMap.protoPackages,
              FileMetadataMapBuilder.REAL_FILE_LOADER,
              new FileMetadataMapBuilder.Extractor<Optional<String>>() {
                @Override
                public
                Optional<String> extractMetadata(Source s, byte[] content)
                throws IOException {
                  CStyleLexer lexer = new CStyleLexer(
                      new String(content, "UTF-8"));
                  return ProtoPackageMap.getPackage(lexer);
                }
              },
              protoSources.sources()));
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to derive proto package metadata", ex);
    }

    protoPackageMapIng.setStoredObject(protoPackageMap);

    try {
      protoPackageMapIng.write();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to write proto package map", ex);
    }
}

  @Override
  public void skip(Log log) throws MojoExecutionException {
    try {
      protoPackageMapIng.read();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to read proto package map", ex);
    }
  }

  @Override
  public ImmutableList<Step> extraSteps(Log log) throws MojoExecutionException {
    return ImmutableList.<Step>of();
  }
}
