package com.google.common.html.plugin.soy;

import java.io.File;
import java.lang.reflect.InvocationTargetException;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.html.plugin.Cheats;
import com.google.common.html.plugin.common.Ingredients.FileIngredient;
import com.google.common.html.plugin.common.Ingredients.FileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients.PathValue;
import com.google.common.html.plugin.plan.PlanKey;
import com.google.common.html.plugin.plan.Step;
import com.google.common.html.plugin.plan.StepSource;
import com.google.common.io.ByteSink;
import com.google.common.io.Files;
import com.google.template.soy.SoyFileSet;

final class SoyToJava extends Step {
  private final SoyFileSet sfs;

  SoyToJava(
      OptionsIngredient<SoyOptions> options,
      FileSetIngredient soySources,
      FileIngredient protoDescriptors,
      PathValue outputJar,
      SoyFileSet sfs) {
    super(
        PlanKey.builder("soy-to-java").addInp(
            options, soySources, protoDescriptors, outputJar)
            .build(),
        ImmutableList.of(options, soySources, protoDescriptors, outputJar),
        Sets.immutableEnumSet(
            StepSource.PROTO_DESCRIPTOR_SET,
            StepSource.SOY_SRC, StepSource.SOY_GENERATED),
        Sets.immutableEnumSet(StepSource.JS_GENERATED));
    this.sfs = sfs;
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    PathValue outputJarPath = (PathValue) inputs.get(3);

    ByteSink classJarOut = Files.asByteSink(outputJarPath.value);
    Optional<ByteSink> srcJarOut = Optional.of(Files.asByteSink(
        new File(
            outputJarPath.value.getParentFile(),
            FilenameUtils.removeExtension(
                outputJarPath.value.getName()) + "-src.jar")));

    try {
      Cheats.cheatCall(
          Void.class, SoyFileSet.class, sfs, "compileToJar",
          ByteSink.class, classJarOut,
          Optional.class, srcJarOut);
      // TODO: expose compileToJar publicly.
    } catch (InvocationTargetException ex) {
      throw new MojoExecutionException(
          "Compilation of templates to a Java JAR failed",
          /* An IOException */ ex.getTargetException());
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
