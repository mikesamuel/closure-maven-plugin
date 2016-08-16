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

  GenerateProtoPackageMap(
      DirScanFileSetIngredient protoSources,
      SerializedObjectIngredient<ProtoPackageMap> protoPackageMap) {
    super(
        PlanKey.builder("generate-proto-package-map")
            .addInp(protoSources, protoPackageMap)
            .build(),
        ImmutableList.<Ingredient>of(protoSources, protoPackageMap),
        Sets.immutableEnumSet(
            StepSource.PROTO_GENERATED, StepSource.PROTO_SRC),
        Sets.immutableEnumSet(
            StepSource.PROTO_PACKAGE_MAP));
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    DirScanFileSetIngredient protoSources =
        (DirScanFileSetIngredient) inputs.get(0);
    SerializedObjectIngredient<ProtoPackageMap> protoPackageMapIng =
        ((SerializedObjectIngredient<?>) inputs.get(1))
        .asSuperType(ProtoPackageMap.class);

    try {
      protoSources.resolve(log);
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to find .proto sources", ex);
    }

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
    SerializedObjectIngredient<ProtoPackageMap> protoPackageMap =
        ((SerializedObjectIngredient<?>) inputs.get(1))
        .asSuperType(ProtoPackageMap.class);

    try {
      protoPackageMap.read();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to read proto package map", ex);
    }
  }

  @Override
  public ImmutableList<Step> extraSteps(Log log) throws MojoExecutionException {
    return ImmutableList.<Step>of();
  }
}
