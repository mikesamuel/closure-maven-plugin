package com.google.closure.plugin.css;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.closure.plugin.common.FileExt;
import com.google.closure.plugin.plan.CompilePlanGraphNode;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;

final class CompileOneBundle
extends CompilePlanGraphNode<CssOptions, CssBundle> {

  CompileOneBundle(PlanContext context, CssOptions options, CssBundle bundle) {
    super(context, options, bundle);
  }

  @Override
  protected void processInputs() throws IOException, MojoExecutionException {
    Preconditions.checkState(bundle.optionsId.equals(options.getId()));

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

    this.outputs = Optional.of(ImmutableList.of(
        cssFile, sourceMapFile));
  }

  static final ImmutableSortedSet<FileExt> FOLLOWER_EXTS =
      ImmutableSortedSet.of(FileExt.JSON);

  @Override
  protected
  Optional<ImmutableList<PlanGraphNode<?>>> rebuildFollowersList(JoinNodes jn) {
    // The CSS rename map produced is a JSON file.
    return Optional.of(jn.followersOf(FOLLOWER_EXTS));
  }

  @Override
  protected SV getStateVector() {
    return new SV(options, bundle, outputs);
  }


  final static class SV
  extends CompilePlanGraphNode.CompileStateVector<CssOptions, CssBundle> {

    private static final long serialVersionUID = -8223372981064559155L;

    SV(CssOptions options, CssBundle bundle,
       Optional<ImmutableList<File>> outputs) {
      super(options, bundle, outputs);
    }

    @Override
    public PlanGraphNode<?> reconstitute(PlanContext context, JoinNodes jn) {
      return new CompileOneBundle(context, options, bundle);
    }
  }
}
