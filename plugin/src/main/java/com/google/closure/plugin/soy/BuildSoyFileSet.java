package com.google.closure.plugin.soy;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.plan.BundlingPlanGraphNode;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.OptionPlanGraphNode.OptionsAndInputs;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;
import com.google.closure.plugin.plan.Update;


final class BuildSoyFileSet
extends BundlingPlanGraphNode<SoyOptions, SoyBundle> {
  BuildSoyFileSet(PlanContext context) {
    super(context);
  }

  @Override
  protected void preExecute(Iterable<? extends PlanGraphNode<?>> preceders) {
    super.preExecute(preceders);
  }

  @Override
  protected ImmutableList<SoyBundle> bundlesFor(
      Optional<ImmutableList<SoyBundle>> oldBundles,
      OptionsAndInputs<SoyOptions> oi)
  throws IOException, MojoExecutionException {

    final ImmutableList<Source> sources = oi.sources;
    final SoyOptions options = oi.options;
    if (sources.isEmpty()) {
      return ImmutableList.of();
    }

    SoyFileSetSupplier sfsSupplier = new SoyFileSetSupplier(oi);
    sfsSupplier.init(context);


    File outputJar = new File(
        context.outputDir, "closure-templates-" + options.getId() + ".jar");
    File jsOutDir = context.genfilesDirs.jsGenfiles;

    return ImmutableList.of(
        new SoyBundle(
            sources, sfsSupplier, outputJar, jsOutDir));
  }

  @Override
  protected SV getStateVector() {
    return new SV(this);
  }

  static final class SV
  extends BundlingPlanGraphNode.BundleStateVector<SoyOptions, SoyBundle> {

    private static final long serialVersionUID = 1L;

    protected SV(BuildSoyFileSet node) {
      super(node);
    }

    @SuppressWarnings("synthetic-access")
    @Override
    public PlanGraphNode<?> reconstitute(PlanContext c, JoinNodes jn) {
      BuildSoyFileSet node = apply(new BuildSoyFileSet(c));
      initSfss(node.optionsAndBundles, c);
      return node;
    }
  }

  static void initSfss(
      Optional<Update<OptionsAndBundles<SoyOptions, SoyBundle>>> opt,
      PlanContext c) {
    if (opt.isPresent()) {
      Update<OptionsAndBundles<SoyOptions, SoyBundle>> u = opt.get();
      for (OptionsAndBundles<SoyOptions, SoyBundle> ob : u.all()) {
        for (SoyBundle b : ob.bundles) {
          b.sfsSupplier.init(c);
        }
      }
    }

  }
}
