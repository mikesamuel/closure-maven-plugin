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
import com.google.common.html.plugin.Sources.Source;
import com.google.common.html.plugin.common.Ingredients.FileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients.PathValue;
import com.google.common.html.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.common.html.plugin.proto.ProtoIO;
import com.google.common.io.Files;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.msgs.SoyMsgBundle;

final class SoyToJs extends AbstractSoyStep {

  SoyToJs(
      OptionsIngredient<SoyOptions> options,
      FileSetIngredient soySources,
      SerializedObjectIngredient<ProtoIO> protoIO,
      PathValue jsOutDir) {
    super("soy-to-js", options, soySources, protoIO, jsOutDir);
  }

  @Override
  protected void compileSoy(
      Log log, SoyOptions options, SoyFileSet.Builder sfsBuilder,
      ImmutableList<Source> sources, PathValue jsOutDir)
  throws MojoExecutionException {

    SoyFileSet sfs = sfsBuilder.build();

    ImmutableList<Js> allJsSrc;
    if (options.js != null && options.js.length != 0) {
      allJsSrc = ImmutableList.copyOf(options.js);
    } else {
      Js js = new Js();
      allJsSrc = ImmutableList.of(js);
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
}
