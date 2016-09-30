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

// TODO: rename since one bundle is no longer true
final class CompileOneBundle
extends CompilePlanGraphNode<CssOptions, CssBundle> {

  CompileOneBundle(PlanContext context) {
    super(context);
  }

  @Override
  protected void process() throws IOException, MojoExecutionException {
    this.outputFiles.clear();

    Update<OptionsAndBundles<CssOptions, CssBundle>> u =
        optionsAndBundles.get();

    for (OptionsAndBundles<CssOptions, CssBundle> ob : u.defunct) {
      for (CssBundle bundle : ob.bundles) {
        CssOptions.Outputs outputs = new CssOptions.Outputs(
            context, ob.optionsAndInputs.options, bundle.entryPoint);
        deleteIfExists(outputs.css);
        deleteIfExists(outputs.sourceMap);
      }
    }

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

    this.outputFiles.add(cssFile);
    this.outputFiles.add(sourceMapFile);
  }


  @Override
  protected SV getStateVector() {
    return new SV(this);
  }


  final static class SV
  extends CompilePlanGraphNode.CompileStateVector<CssOptions, CssBundle> {

    private static final long serialVersionUID = -8223372981064559155L;

    SV(CompileOneBundle node) {
      super(node);
    }

    @Override
    public PlanGraphNode<?> reconstitute(PlanContext context, JoinNodes jn) {
      return apply(new CompileOneBundle(context));
    }
  }
}
