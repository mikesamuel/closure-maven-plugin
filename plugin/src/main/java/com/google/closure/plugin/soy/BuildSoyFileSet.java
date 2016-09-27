package com.google.closure.plugin.soy;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.collect.ImmutableList;
import com.google.closure.plugin.common.DirectoryScannerSpec;
import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.common.Sources;
import com.google.closure.plugin.plan.CompilePlanGraphNode;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.OptionPlanGraphNode;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;
import com.google.closure.plugin.plan.TeePlanGraphNode;

final class BuildSoyFileSet extends OptionPlanGraphNode<SoyOptions> {
  BuildSoyFileSet(PlanContext context) {
    super(context);
  }

  @Override
  protected PlanGraphNode<?> fanOutTo(SoyOptions options)
  throws MojoExecutionException {
    DirectoryScannerSpec spec = options.toDirectoryScannerSpec(context);

    Sources soySources;

    try {
      soySources = Sources.scan(context.log, spec);
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to find .soy sources", ex);
    }
    context.log.debug("Found " + soySources.sources.size() + " soy sources");

    final ImmutableList<Source> sources = soySources.sources;
    if (sources.isEmpty()) {
      return new TeePlanGraphNode(context);
    }

    SoyFileSetSupplier sfsSupplier = new SoyFileSetSupplier();

    File outputJar = new File(
        context.outputDir, "closure-templates-" + options.getId() + ".jar");
    File jsOutDir = context.genfilesDirs.jsGenfiles;

    SoyBundle soyBundle = new SoyBundle(sources, sfsSupplier);

    return new TeePlanGraphNode(
        context,
        new SoyToJs(context, options, soyBundle, jsOutDir),
        new SoyToJava(context, options, soyBundle, outputJar));
  }



  @Override
  protected SoyOptions getOptionsForFollower(PlanGraphNode<?> follower) {
    TeePlanGraphNode tee = (TeePlanGraphNode) follower;
    for (PlanGraphNode<?> grandchild : tee.getFollowerList()) {
      if (grandchild instanceof CompilePlanGraphNode<?, ?>) {
        return (SoyOptions) ((CompilePlanGraphNode<?, ?>) grandchild).options;
      }
    }
    return new SoyOptions();  // HACK
  }

  @Override
  protected SV getStateVector() {
    return new SV(ImmutableList.copyOf(this.getOptionSets()));
  }

  static final class SV
  extends OptionPlanGraphNode.OptionStateVector<SoyOptions> {

    private static final long serialVersionUID = 2873057577418329113L;

    protected SV(ImmutableList<SoyOptions> optionSets) {
      super(optionSets);
    }

    @Override
    public PlanGraphNode<?> reconstitute(PlanContext c, JoinNodes jn) {
      BuildSoyFileSet n = new BuildSoyFileSet(c);
      n.setOptionSets(this.optionSets);
      return n;
    }

  }
}
