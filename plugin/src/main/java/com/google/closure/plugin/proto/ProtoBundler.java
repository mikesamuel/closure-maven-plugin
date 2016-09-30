package com.google.closure.plugin.proto;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.closure.plugin.common.SourceFileProperty;
import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.Metadata;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;
import com.google.closure.plugin.plan.RebundlingPlanGraphNode;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

final class ProtoBundler
extends RebundlingPlanGraphNode<ProtoFinalOptions, ProtoPackageMap, ProtoBundle>
{
  ProtoBundler(PlanContext context) {
    super(context);
  }

  @Override
  protected ImmutableList<ProtoBundle> bundlesFor(
      Optional<ImmutableList<ProtoBundle>> oldBundles,
      OptionsAndBundles<ProtoFinalOptions, ProtoPackageMap> ob)
  throws IOException, MojoExecutionException {
    Preconditions.checkState(ob.bundles.size() == 1);
    return makeBundles(
        ob.optionsAndInputs.options,
        ob.optionsAndInputs.sources,
        ob.bundles.get(0));
  }

  ImmutableList<ProtoBundle> makeBundles(
      final ProtoFinalOptions options,
      final ImmutableList<Source> protoSources,
      final ProtoPackageMap packageMap) {
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
            Iterables.filter(protoSources, inputFilter);

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
  protected SV getStateVector() {
    return new SV(this);
  }


  static final class SV
  extends RebundlingPlanGraphNode.RebundleStateVector<
      ProtoFinalOptions, ProtoPackageMap, ProtoBundle
  > {
    private static final long serialVersionUID = 1L;

    protected SV(ProtoBundler node) {
      super(node);
    }

    @Override
    public PlanGraphNode<?> reconstitute(PlanContext c, JoinNodes joinNodes) {
      return apply(new ProtoBundler(c));
    }
  }
}
