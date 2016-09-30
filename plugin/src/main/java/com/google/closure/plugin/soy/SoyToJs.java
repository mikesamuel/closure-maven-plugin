package com.google.closure.plugin.soy;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.plan.BundlingPlanGraphNode.OptionsAndBundles;
import com.google.closure.plugin.plan.CompilePlanGraphNode;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;
import com.google.closure.plugin.plan.Update;
import com.google.common.io.Files;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.msgs.SoyMsgBundle;

final class SoyToJs extends CompilePlanGraphNode<SoyOptions, SoyBundle> {

  SoyToJs(PlanContext context) {
    super(context);
  }

  @Override
  protected void process() throws IOException, MojoExecutionException {
    this.outputFiles.clear();

    Update<OptionsAndBundles<SoyOptions, SoyBundle>> u =
        optionsAndBundles.get();

    // TODO: delete defunct.

    for (OptionsAndBundles<SoyOptions, SoyBundle> c : u.changed) {
      for (SoyBundle b : c.bundles) {
        processOne(c.optionsAndInputs.options, b);
      }
    }
  }

  protected void processOne(SoyOptions options, SoyBundle bundle)
  throws IOException, MojoExecutionException {
    SoyFileSet sfs = bundle.sfsSupplier.getSoyFileSet(context);

    ImmutableList<Js> allJsSrc = ImmutableList.copyOf(options.getJs());

    ImmutableList<Source> sources = bundle.inputs;

    for (Js js : allJsSrc) {
      SoyJsSrcOptions jsSrcOptions = js.toSoyJsSrcOptions(context.log);
      SoyMsgBundle msgBundle = null;

      // TODO: relay errors and warnings via build context.
      List<String> jsFileContent = sfs.compileToJsSrc(jsSrcOptions, msgBundle);
      java.nio.file.Files.createDirectories(bundle.jsOutDir.toPath());

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
            bundle.jsOutDir.getPath(), outputRelPath.getPath()));
        Files.createParentDirs(outputPath);
        try {
          Files.write(compiledJsContent, outputPath, Charsets.UTF_8);
        } catch (IOException ex) {
          throw new MojoExecutionException(
              "Failed to write soy templates compiled from " + inputRelPath
              + " to " + outputPath,
              ex);
        }
        outputFiles.add(outputPath);
      }
    }
  }

  @Override
  protected SV getStateVector() {
    return new SV(this);
  }

  static final class SV
  extends CompilePlanGraphNode.CompileStateVector<SoyOptions, SoyBundle> {

    private static final long serialVersionUID = 1L;

    protected SV(SoyToJs node) {
      super(node);
    }

    @Override
    public PlanGraphNode<?> reconstitute(PlanContext context, JoinNodes jn) {
      SoyToJs node = apply(new SoyToJs(context));
      BuildSoyFileSet.initSfss(node.optionsAndBundles, context);
      return node;
    }
  }
}
