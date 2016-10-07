package com.google.closure.plugin.css;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.closure.plugin.plan.BundlingPlanGraphNode.OptionsAndBundles;
import com.google.closure.plugin.plan.CompilePlanGraphNode;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;
import com.google.closure.plugin.plan.Update;
import com.google.common.collect.ImmutableList;

final class CompileCss
extends CompilePlanGraphNode<CssOptions, CssBundle> {

  CompileCss(PlanContext context) {
    super(context);
  }

  @Override
  protected void process() throws IOException, MojoExecutionException {
    this.changedFiles.clear();

    this.processDefunctBundles(optionsAndBundles);

    Update<OptionsAndBundles<CssOptions, CssBundle>> u =
        optionsAndBundles.get();

    for (OptionsAndBundles<CssOptions, CssBundle> ob : u.allExtant()) {
      for (CssBundle b : ob.bundles) {
        processOneBundle(ob.optionsAndInputs.options, b);
      }
    }
  }

  void processOneBundle(CssOptions options, CssBundle bundle)
  throws MojoExecutionException {

    File cssFile = bundle.outputs.css;
    File sourceMapFile = bundle.outputs.sourceMap;

    boolean ok;
    try {
      ok = new CssCompilerWrapper()
          .cssOptions(options)
          .inputs(bundle.inputs)
          .outputFile(cssFile)
          .sourceMapFile(sourceMapFile)
          .substitutionMapProvider(context.substitutionMapProvider)
          .compileCss(context.buildContext, context.log);
    } catch (IOException ex) {
      context.log.error(ex);
      ok = false;
    }
    if (!ok) {
      throw new MojoExecutionException(
          "Failed to compile CSS " + bundle.entryPoint.relativePath);
    }

    this.changedFiles.add(cssFile);
    this.changedFiles.add(sourceMapFile);
    this.bundleToOutputs.put(bundle, ImmutableList.of(cssFile, sourceMapFile));
  }


  @Override
  protected SV getStateVector() {
    return new SV(this);
  }


  final static class SV
  extends CompilePlanGraphNode.CompileStateVector<CssOptions, CssBundle> {

    private static final long serialVersionUID = -8223372981064559155L;

    SV(CompileCss node) {
      super(node);
    }

    @Override
    public PlanGraphNode<?> reconstitute(PlanContext context, JoinNodes jn) {
      return apply(new CompileCss(context));
    }
  }
}
