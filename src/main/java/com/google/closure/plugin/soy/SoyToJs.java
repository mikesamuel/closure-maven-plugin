package com.google.common.html.plugin.soy;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.html.plugin.common.Ingredients.FileIngredient;
import com.google.common.html.plugin.common.Ingredients.FileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.HashedInMemory;
import com.google.common.html.plugin.common.Ingredients.PathValue;
import com.google.common.html.plugin.common.Sources.Source;
import com.google.common.html.plugin.plan.PlanKey;
import com.google.common.html.plugin.plan.Step;
import com.google.common.html.plugin.plan.StepSource;
import com.google.common.io.Files;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.msgs.SoyMsgBundle;

final class SoyToJs extends Step {

  private final SoyFileSet sfs;

  SoyToJs(
      HashedInMemory<SoyOptions> options,
      FileSetIngredient soySources,
      FileIngredient protoDescriptors,
      PathValue jsOutDir,
      SoyFileSet sfs) {
    super(
        PlanKey.builder("soy-to-js").addInp(
            options, soySources, protoDescriptors, jsOutDir)
            .build(),
        ImmutableList.of(options, soySources, protoDescriptors, jsOutDir),
        Sets.immutableEnumSet(
            StepSource.PROTO_DESCRIPTOR_SET,
            StepSource.SOY_SRC, StepSource.SOY_GENERATED),
        Sets.immutableEnumSet(StepSource.JS_GENERATED));
    this.sfs = sfs;
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    HashedInMemory<SoyOptions> optionsIng =
        ((HashedInMemory<?>) inputs.get(0))
        .asSuperType(SoyOptions.class);
    FileSetIngredient soySources = (FileSetIngredient) inputs.get(1);
    PathValue jsOutDir = (PathValue) inputs.get(3);

    SoyOptions options = optionsIng.getValue();

    ImmutableList<Js> allJsSrc = ImmutableList.copyOf(options.js);

    ImmutableList<Source> sources;
    {
      ImmutableList.Builder<Source> b = ImmutableList.builder();
      for (FileIngredient f : soySources.sources()) {
        b.add(f.source);
      }
      sources = b.build();
    }

    for (Js js : allJsSrc) {
      SoyJsSrcOptions jsSrcOptions = js.toSoyJsSrcOptions(log);
      SoyMsgBundle msgBundle = null;

      List<String> jsFileContent = sfs.compileToJsSrc(jsSrcOptions, msgBundle);
      File outputDir = jsOutDir.value;
      outputDir.mkdirs();

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
            outputDir.getPath(), outputRelPath.getPath()));
        outputPath.getParentFile().mkdirs();
        try {
          Files.write(compiledJsContent, outputPath, Charsets.UTF_8);
        } catch (IOException ex) {
          throw new MojoExecutionException(
              "Failed to write soy templates compiled from " + inputRelPath
              + " to " + outputPath,
              ex);
        }
      }
    }
  }

  @Override
  public void skip(Log log) throws MojoExecutionException {
    // All done.
  }

  @Override
  public ImmutableList<Step> extraSteps(Log log) throws MojoExecutionException {
    return ImmutableList.of();
  }
}
