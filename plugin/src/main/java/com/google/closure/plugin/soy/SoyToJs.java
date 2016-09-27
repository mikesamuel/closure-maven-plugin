package com.google.closure.plugin.soy;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.closure.plugin.common.FileExt;
import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.plan.CompilePlanGraphNode;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;
import com.google.common.io.Files;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.msgs.SoyMsgBundle;

final class SoyToJs extends CompilePlanGraphNode<SoyOptions, SoyBundle> {

  final File jsOutDir;

  SoyToJs(
      PlanContext context,
      SoyOptions options,
      SoyBundle bundle,
      File jsOutDir) {
    super(context, options, bundle);
    this.jsOutDir = jsOutDir;
  }

  @Override
  protected void processInputs() throws IOException, MojoExecutionException {
    bundle.sfsSupplier.init(context, options, bundle.inputs);
    SoyFileSet sfs = bundle.sfsSupplier.getSoyFileSet();

    ImmutableList<Js> allJsSrc = ImmutableList.copyOf(options.getJs());

    ImmutableList<Source> sources = bundle.inputs;

    ImmutableList.Builder<File> allOutputFiles = ImmutableList.builder();
    for (Js js : allJsSrc) {
      SoyJsSrcOptions jsSrcOptions = js.toSoyJsSrcOptions(context.log);
      SoyMsgBundle msgBundle = null;

      // TODO: relay errors and warnings via build context.
      List<String> jsFileContent = sfs.compileToJsSrc(jsSrcOptions, msgBundle);
      java.nio.file.Files.createDirectories(jsOutDir.toPath());

      int nOutputs = jsFileContent.size();
      Preconditions.checkState(nOutputs == sources.size());
      for (int i = 0; i < nOutputs; ++i) {
        File inputRelPath = sources.get(i).relativePath;

        // Disambiguate with js.id if not null (constructed above).
        String suffix = ".js";
        if (!js.wasIdImplied()) {
          suffix = "_" + js.getId() + suffix;
        }

        String compiledJsContent = jsFileContent.get(i);
        File outputRelPath = new File(
          inputRelPath.getParentFile(),
          FilenameUtils.getBaseName(inputRelPath.getName()) + suffix);
        File outputPath = new File(FilenameUtils.concat(
            jsOutDir.getPath(), outputRelPath.getPath()));
        Files.createParentDirs(outputPath);
        try {
          Files.write(compiledJsContent, outputPath, Charsets.UTF_8);
        } catch (IOException ex) {
          throw new MojoExecutionException(
              "Failed to write soy templates compiled from " + inputRelPath
              + " to " + outputPath,
              ex);
        }
        allOutputFiles.add(outputPath);
      }
    }
    outputs = Optional.of(allOutputFiles.build());
  }

  @Override
  protected
  Optional<ImmutableList<PlanGraphNode<?>>> rebuildFollowersList(JoinNodes jn)
  throws MojoExecutionException {
    return Optional.of(jn.followersOf(FileExt.JS));
  }

  @Override
  protected SV getStateVector() {
    return new SV(options, bundle, outputs, jsOutDir);
  }

  static final class SV
  extends CompilePlanGraphNode.CompileStateVector<SoyOptions, SoyBundle> {

    private static final long serialVersionUID = -5624818900455357167L;

    final File jsOutDir;

    protected SV(
        SoyOptions options, SoyBundle bundle,
        Optional<ImmutableList<File>> outputs,
        File jsOutDir) {
      super(options, bundle, outputs);
      this.jsOutDir = jsOutDir;
    }

    @SuppressWarnings("synthetic-access")
    @Override
    public PlanGraphNode<?> reconstitute(PlanContext context, JoinNodes jn) {
      SoyToJs n = new SoyToJs(context, options, bundle, jsOutDir);
      n.outputs = outputs;
      return n;
    }
  }
}
