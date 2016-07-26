package com.google.common.html.plugin.soy;

import java.io.File;
import java.lang.reflect.InvocationTargetException;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.html.plugin.Cheats;
import com.google.common.html.plugin.Sources.Source;
import com.google.common.html.plugin.common.Ingredients.FileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients.PathValue;
import com.google.common.html.plugin.common.Ingredients.SerializedObjectIngredient;
import com.google.common.html.plugin.proto.ProtoIO;
import com.google.common.io.ByteSink;
import com.google.common.io.Files;
import com.google.template.soy.SoyFileSet;

final class SoyToJava extends AbstractSoyStep {

  SoyToJava(
      OptionsIngredient<SoyOptions> options,
      FileSetIngredient soySources,
      SerializedObjectIngredient<ProtoIO> protoIO,
      PathValue outputJar) {
    super("soy-to-java", options, soySources, protoIO, outputJar);
  }

  @Override
  protected void compileSoy(
      Log log, SoyOptions options, SoyFileSet.Builder sfsBuilder,
      ImmutableList<Source> sources, PathValue outputJarPath)
  throws MojoExecutionException {

    ByteSink classJarOut = Files.asByteSink(outputJarPath.value);
    Optional<ByteSink> srcJarOut = Optional.of(Files.asByteSink(
        new File(
            outputJarPath.value.getParentFile(),
            FilenameUtils.removeExtension(
                outputJarPath.value.getName()) + "-src.jar")));

    try {
      Cheats.cheatCall(
          Void.class, SoyFileSet.class, sfsBuilder.build(), "compileToJar",
          ByteSink.class, classJarOut,
          Optional.class, srcJarOut);
      // TODO: expose compileToJar publicly.
    } catch (InvocationTargetException ex) {
      throw new MojoExecutionException(
          "Compilation of templates to a Java JAR failed",
          /* An IOException */ ex.getTargetException());
    }
  }
}
