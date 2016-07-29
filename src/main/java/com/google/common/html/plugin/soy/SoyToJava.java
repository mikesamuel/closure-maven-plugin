package com.google.common.html.plugin.soy;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.html.plugin.common.Ingredients.Bundle;
import com.google.common.html.plugin.common.Ingredients.FileIngredient;
import com.google.common.html.plugin.common.Ingredients.FileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients.PathValue;
import com.google.common.html.plugin.common.Ingredients.UriValue;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.PlanKey;
import com.google.common.html.plugin.plan.Step;
import com.google.common.html.plugin.plan.StepSource;
import com.google.common.io.ByteSink;
import com.google.common.io.Files;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.jbcsrc.JbcsrcOptions;

final class SoyToJava extends Step {
  private final SoyFileSet sfs;

  /**
   * @param protoDescriptors the path to the .fd file.
   * @param protobufClassPath a class path that can be used to load the
   *     generated message classes so that the compiler can introspect when
   *     generating bytecode that interfaces with protobuf instances.
   */
  SoyToJava(
      OptionsIngredient<SoyOptions> options,
      FileSetIngredient soySources,
      FileIngredient protoDescriptors,
      Bundle<UriValue> protobufClassPath,
      PathValue outputJar,
      SoyFileSet sfs) {
    super(
        PlanKey.builder("soy-to-java").addInp(
            options, soySources, protoDescriptors, protobufClassPath, outputJar)
            .build(),
        ImmutableList.<Ingredient>of(
            options,
            soySources,
            protoDescriptors,
            protobufClassPath,
            outputJar),
        Sets.immutableEnumSet(
            StepSource.PROTO_DESCRIPTOR_SET,
            StepSource.SOY_SRC, StepSource.SOY_GENERATED),
        Sets.immutableEnumSet(StepSource.JS_GENERATED));
    this.sfs = sfs;
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    Bundle<UriValue> protobufClassPathElements =
        ((Bundle<?>) inputs.get(3)).asSuperType(
            new Function<Ingredient, UriValue>() {
              @Override
              public UriValue apply(Ingredient input) {
                return (UriValue) input;
              }
            });
    PathValue outputJarPath = (PathValue) inputs.get(4);

    ByteSink classJarOut = Files.asByteSink(outputJarPath.value);
    Optional<ByteSink> srcJarOut = Optional.of(Files.asByteSink(
        new File(
            outputJarPath.value.getParentFile(),
            FilenameUtils.removeExtension(
                outputJarPath.value.getName()) + "-src.jar")));

    ImmutableList.Builder<URL> protobufClassPathUrls = ImmutableList.builder();
    for (UriValue protobufClassPathElement : protobufClassPathElements.ings) {
      try {
        protobufClassPathUrls.add(protobufClassPathElement.value.toURL());
      } catch (MalformedURLException ex) {
        throw new MojoExecutionException(
            "Failed to convert classpath element"
            + " to form needed for class loader",
            ex);
      }
    }

    try {
      try (URLClassLoader protobufClassLoader = new URLClassLoader(
            protobufClassPathUrls.build().toArray(new URL[0]),
            getClass().getClassLoader())) {
        JbcsrcOptions options = JbcsrcOptions.builder()
            .withProtobufClassLoader(protobufClassLoader)
            .build();
        sfs.compileToJar(options, classJarOut, srcJarOut);
      }
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to compile soy to JAR", ex);
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
