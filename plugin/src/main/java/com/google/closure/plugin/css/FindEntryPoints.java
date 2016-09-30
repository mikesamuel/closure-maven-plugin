package com.google.closure.plugin.css;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.closure.plugin.common.Sources;
import com.google.closure.plugin.css.CssDepGraph.Dependencies;
import com.google.closure.plugin.plan.BundlingPlanGraphNode;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.OptionPlanGraphNode.OptionsAndInputs;
import com.google.closure.plugin.plan.PlanContext;


final class FindEntryPoints
extends BundlingPlanGraphNode<CssOptions, CssBundle> {

  FindEntryPoints(PlanContext context) {
    super(context);
  }

  @Override
  protected ImmutableList<CssBundle> bundlesFor(
      Optional<ImmutableList<CssBundle>> oldBundles,
      OptionsAndInputs<CssOptions> oi)
  throws IOException, MojoExecutionException {

    CssOptions options = oi.options;
    ImmutableList.Builder<CssBundle> b = ImmutableList.builder();

    CssDepGraph importGraph = new CssDepGraph(context.log, oi.sources);

    File cssOutputDirectory = new File(context.closureOutputDirectory, "css");
    for (Sources.Source entryPoint : importGraph.entryPoints) {
      Dependencies deps = importGraph.transitiveClosureDeps(entryPoint);
      if (!deps.foundAllStatic) {
        throw new MojoExecutionException(
            "Failed to resolve all dependencies of "
                + entryPoint.canonicalPath);
      }
      String basePath = cssOutputDirectory.getPath();
      if (!(basePath.isEmpty() || basePath.endsWith(File.separator))) {
        basePath += File.separator;
      }
      CssOptions.Outputs cssCompilerOutputs = new CssOptions.Outputs(
          context, options, entryPoint);
      b.add(new CssBundle(
          options.getId(), entryPoint, deps.allDependencies,
          cssCompilerOutputs));
    }


    ImmutableList<CssBundle> bundles = b.build();
    return bundles;
  }

  @Override
  protected SV getStateVector() {
    return new SV(this);
  }

  static final class SV
  extends BundlingPlanGraphNode.BundleStateVector<CssOptions, CssBundle> {

    private static final long serialVersionUID = 1L;

    SV(FindEntryPoints fe) {
      super(fe);
    }

    @Override
    public FindEntryPoints reconstitute(PlanContext context, JoinNodes jn) {
      return apply(new FindEntryPoints(context));
    }
  }
}
