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
import com.google.closure.plugin.common.CStyleLexer;
import com.google.closure.plugin.common.DirectoryScannerSpec;
import com.google.closure.plugin.common.Sources;
import com.google.closure.plugin.common.SourceFileProperty;
import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.plan.BundlingPlanGraphNode;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.Metadata;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;
import com.google.closure.plugin.plan.SourceMetadataMapBuilder;


final class GenerateProtoPackageMap
extends BundlingPlanGraphNode<ProtoFinalOptions, ProtoBundle> {
  private Optional<Sources> sources = Optional.absent();
  private Optional<ProtoPackageMap> protoPackageMap = Optional.absent();

  GenerateProtoPackageMap(PlanContext context, ProtoFinalOptions options) {
    super(context, options);
  }

  @Override
  protected void processInputs() throws IOException, MojoExecutionException {
    Sources protoSources = Sources.scan(context.log, options.sources);
    this.sources = Optional.of(protoSources);

    ProtoPackageMap oldMap = protoPackageMap.or(ProtoPackageMap.EMPTY);

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
              protoSources.sources));
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to derive proto package metadata", ex);
    }

    this.protoPackageMap = Optional.of(newProtoPackageMap);
    this.bundles = Optional.of(makeBundles(newProtoPackageMap));
  }

  ImmutableList<ProtoBundle> makeBundles(final ProtoPackageMap packageMap) {
    final Sources protoSources = this.sources.get();
    final Log log = context.log;
    final ImmutableSet<String> javaOnly = options.javaOnly;
    final ImmutableSet<String> jsOnly = options.jsOnly;

    Predicate<Source> isTestOnly = new Predicate<Source>() {
      @Override
      public boolean apply(Source source) {
        return source.root.ps.contains(SourceFileProperty.TEST_ONLY);
      }
    };
    Predicate<Source> isDep = new Predicate<Source>() {
      @Override
      public boolean apply(Source source) {
        return source.root.ps.contains(SourceFileProperty.LOAD_AS_NEEDED);
      }
    };
    Predicate<Source> inJavaOnlySet = new Predicate<Source>() {
      @Override
      public boolean apply(Source source) {
        Metadata<Optional<String>> packageMd = packageMap.protoPackages.get(
            source);
        if (packageMd == null) {
          log.warn("Missing package metadata for " + source.canonicalPath);
          return false;  // Leave in all set.
        }
        Optional<String> packageName = packageMd.metadata;
        return packageName.isPresent()
            && javaOnly.contains(packageName.get());
      }
    };
    Predicate<Source> inJsOnlySet = new Predicate<Source>() {
      @Override
      public boolean apply(Source source) {
        Metadata<Optional<String>> packageMd = packageMap.protoPackages.get(
            source);
        if (packageMd == null) {
          return false;  // Leave in all set.
        }
        Optional<String> packageName = packageMd.metadata;
        return packageName.isPresent()
            && jsOnly.contains(packageName.get());
      }
    };

    ImmutableList.Builder<ProtoBundle> protoBundles = ImmutableList.builder();

    RunProtoc.RootSet[] roots = RunProtoc.RootSet.values();
    RunProtoc.LangSet[] langs = RunProtoc.LangSet.values();

    for (RunProtoc.RootSet root : roots) {
      for (RunProtoc.LangSet lang : langs) {
        Predicate<Source> rootFilter = null;
        Optional<File> descriptorSet = Optional.absent();
        switch (root) {
          case MAIN:
            rootFilter = Predicates.not(isTestOnly);
            descriptorSet = context.protoIO.mainDescriptorSetFile;
            break;
          case TEST:
            rootFilter = isTestOnly;
            descriptorSet = context.protoIO.testDescriptorSetFile;
            break;
        }
        assert rootFilter != null && descriptorSet != null;

        Predicate<Source> langFilter = null;
        switch (lang) {
          case ALL:
            @SuppressWarnings("unchecked")
            Predicate<Source> notSpecialOrDep = Predicates.not(
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

        Predicate<Source> inputFilter = Predicates.and(
            rootFilter,
            langFilter);

        Iterable<Source> filteredSources =
            Iterables.filter(protoSources.sources, inputFilter);

        // Compile the main files separately from the test files since protoc
        // has a single output directory.
        if (!Iterables.isEmpty(filteredSources)) {
          protoBundles.add(new ProtoBundle(
              root, lang, ImmutableList.copyOf(filteredSources),
              descriptorSet));
        }
      }
    }

    return protoBundles.build();
  }

  @Override
  protected DirectoryScannerSpec getSourceSpec() {
    return options.sources;
  }

  @Override
  protected void markOutputs() {
    // Done.
  }

  @Override
  protected PlanGraphNode<?> fanOutTo(ProtoBundle bundle) {
    return new RunProtoc(context, options, bundle);
  }

  @Override
  protected ProtoBundle bundleForFollower(PlanGraphNode<?> f) {
    return ((RunProtoc) f).bundle;
  }

  @Override
  protected SV getStateVector() {
    return new SV(options, bundles.get(), protoPackageMap.get());
  }

  static final class SV
  extends BundleStateVector<ProtoFinalOptions, ProtoBundle> {

    private static final long serialVersionUID = 1L;

    final ImmutableList<ProtoBundle> bundles;
    final ProtoPackageMap protoPackageMap;

    protected SV(
        ProtoFinalOptions options, ImmutableList<ProtoBundle> bundles,
        ProtoPackageMap protoPackageMap) {
      super(options);
      this.bundles = bundles;
      this.protoPackageMap = protoPackageMap;
    }

    @Override
    public ImmutableList<ProtoBundle> getBundles() {
      return bundles;
    }

    @SuppressWarnings("synthetic-access")
    @Override
    public GenerateProtoPackageMap reconstitute(PlanContext c, JoinNodes jn) {
      GenerateProtoPackageMap n = new GenerateProtoPackageMap(c, options);
      n.bundles = Optional.of(bundles);
      n.protoPackageMap = Optional.of(protoPackageMap);
      return n;
    }
  }
}
