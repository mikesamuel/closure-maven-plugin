package com.google.closure.plugin.css;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.closure.plugin.common.DirectoryScannerSpec;
import com.google.closure.plugin.common.Sources;
import com.google.closure.plugin.css.CssDepGraph.Dependencies;
import com.google.closure.plugin.plan.BundlingPlanGraphNode;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;

final class FindEntryPoints
extends BundlingPlanGraphNode<CssOptions, CssBundle> {

  FindEntryPoints(PlanContext context, CssOptions options) {
    super(context, options);
  }

  void setBundleList(Iterable<CssBundle> newBundles) {
    this.bundles = Optional.of(ImmutableList.copyOf(newBundles));
  }

  static final class SV
  extends BundlingPlanGraphNode.BundleStateVector<CssOptions, CssBundle> {

    private static final long serialVersionUID = 1906971648583331481L;

    final CssBundleList bundles;

    SV(CssOptions options, CssBundleList bundles) {
      super(options);
      this.bundles = bundles;
    }

    @Override
    public ImmutableList<CssBundle> getBundles() {
      return bundles.bundles;
    }

    @Override
    public FindEntryPoints reconstitute(PlanContext context, JoinNodes jn) {
      FindEntryPoints node = new FindEntryPoints(context, this.options);
      node.setBundleList(getBundles());
      return node;
    }
  }

  @Override
  protected DirectoryScannerSpec getSourceSpec() {
    return options.toDirectoryScannerSpec(context);
  }

  @Override
  protected void processInputs() throws IOException, MojoExecutionException {
    DirectoryScannerSpec dsSpec = getSourceSpec();
    Sources cssSources;
    try {
      cssSources = Sources.scan(context.log, dsSpec);
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to find source files", ex);
    }

    ImmutableList.Builder<CssBundle> b = ImmutableList.builder();

    CssDepGraph importGraph;
    try {
      importGraph = new CssDepGraph(context.log, cssSources.sources);
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to parse imports in CSS source files", ex);
    }

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

    this.bundles = Optional.of(b.build());
  }

  @Override
  protected SV getStateVector() {
    return new SV(getOptions(), new CssBundleList(bundles.get()));
  }

  @Override
  protected PlanGraphNode<?> fanOutTo(CssBundle bundle) {
    return new CompileOneBundle(context, options, bundle);
  }

  @Override
  protected CssBundle bundleForFollower(PlanGraphNode<?> f) {
    return ((CompileOneBundle) f).bundle;
  }
}
