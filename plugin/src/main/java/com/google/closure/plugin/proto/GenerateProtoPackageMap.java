package com.google.closure.plugin.proto;

import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.closure.plugin.common.CStyleLexer;
import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.plan.BundlingPlanGraphNode;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.OptionPlanGraphNode.OptionsAndInputs;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.SourceMetadataMapBuilder;


final class GenerateProtoPackageMap
extends BundlingPlanGraphNode<ProtoFinalOptions, ProtoPackageMap> {

  GenerateProtoPackageMap(PlanContext context) {
    super(context);
  }

  @Override
  protected ImmutableList<ProtoPackageMap> bundlesFor(
      Optional<ImmutableList<ProtoPackageMap>> oldBundles,
      OptionsAndInputs<ProtoFinalOptions> oi)
  throws IOException, MojoExecutionException {
    ImmutableList<Source> protoSources = oi.sources;

    ProtoPackageMap oldMap = oldBundles.isPresent()
        ? oldBundles.get().get(0)
        : ProtoPackageMap.EMPTY;

    ProtoPackageMap newProtoPackageMap;
    try {
      newProtoPackageMap = new ProtoPackageMap(
          SourceMetadataMapBuilder.updateFromSources(
              oldMap.protoPackages,
              SourceMetadataMapBuilder.REAL_FILE_LOADER,
              new SourceMetadataMapBuilder.Extractor<Optional<String>>() {
                @Override
                public
                Optional<String> extractMetadata(Source s, byte[] content)
                throws IOException {
                  CStyleLexer lexer = new CStyleLexer(
                      new String(content, "UTF-8"));
                  return ProtoPackageMap.getPackage(lexer);
                }
              },
              protoSources));
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to derive proto package metadata", ex);
    }

    return ImmutableList.of(newProtoPackageMap);
  }

  @Override
  protected SV getStateVector() {
    return new SV(this);
  }

  static final class SV
  extends BundleStateVector<ProtoFinalOptions, ProtoPackageMap> {
    private static final long serialVersionUID = 1L;

    protected SV(GenerateProtoPackageMap node) {
      super(node);
    }

    @Override
    public GenerateProtoPackageMap reconstitute(PlanContext c, JoinNodes jn) {
      return apply(new GenerateProtoPackageMap(c));
    }
  }
}
