package com.google.closure.plugin.soy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.closure.plugin.common.Ingredients.FileIngredient;
import com.google.closure.plugin.common.Ingredients.FileSetIngredient;
import com.google.closure.plugin.common.Ingredients.HashedInMemory;
import com.google.closure.plugin.common.Ingredients.PathValue;
import com.google.closure.plugin.plan.Ingredient;
import com.google.closure.plugin.plan.PlanKey;
import com.google.closure.plugin.plan.Step;
import com.google.closure.plugin.plan.StepSource;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteStreams;
import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.SoyToJbcSrcCompiler;

final class SoyToJava extends Step {
  private final SoyFileSet sfs;

  /**
   * @param protoDescriptors the path to the .fd file.
   * @param protobufClassPath a class path that can be used to load the
   *     generated message classes so that the compiler can introspect when
   *     generating bytecode that interfaces with protobuf instances.
   */
  SoyToJava(
      HashedInMemory<SoyOptions> options,
      FileSetIngredient soySources,
      FileIngredient protoDescriptors,
      PathValue outputJar,
      PathValue projectBuildOutputDirectory,
      SoyFileSet sfs) {
    super(
        PlanKey.builder("soy-to-java").addInp(
            options, soySources, protoDescriptors,
            outputJar, projectBuildOutputDirectory)
            .build(),
        ImmutableList.<Ingredient>of(
            options,
            soySources,
            protoDescriptors,
            outputJar,
            projectBuildOutputDirectory),
        Sets.immutableEnumSet(
            StepSource.PROTO_DESCRIPTOR_SET,
            StepSource.SOY_SRC, StepSource.SOY_GENERATED),
        Sets.immutableEnumSet(StepSource.JS_GENERATED));
    this.sfs = sfs;
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    PathValue outputJarPath = (PathValue) inputs.get(3);
    PathValue projectBuildOutputDirectoryValue = (PathValue) inputs.get(4);

    final File classJarOutFile = outputJarPath.value;
    final File srcJarOutFile = new File(
        outputJarPath.value.getParentFile(),
        FilenameUtils.removeExtension(
            outputJarPath.value.getName()) + "-src.jar");

    // Compile To Jar
    final FileWriteMode[] writeModes = new FileWriteMode[0];
    ByteSink classJarOut = Files.asByteSink(classJarOutFile, writeModes);
    Optional<ByteSink> srcJarOut = Optional.of(
        Files.asByteSink(srcJarOutFile, writeModes));
    try {
      SoyToJbcSrcCompiler.compile(sfs, classJarOut, srcJarOut);
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to write compiled Soy output to a JAR", ex);
    }

    // Unpack JAR into classes directory.
    File projectBuildOutputDirectory = projectBuildOutputDirectoryValue.value;
    try {
      try (InputStream in = new FileInputStream(classJarOutFile)) {
        try (ZipInputStream zipIn = new ZipInputStream(in)) {
          for (ZipEntry entry; (entry = zipIn.getNextEntry()) != null;
              zipIn.closeEntry()) {
            if (entry.isDirectory()) {
              continue;
            }
            String name = Files.simplifyPath(entry.getName());
            if (name.startsWith("META-INF")) { continue; }
            log.debug("Unpacking " + name + " from soy generated jar");
            File outputFile = new File(FilenameUtils.concat(
                projectBuildOutputDirectory.getPath(), name));
            outputFile.getParentFile().mkdirs();
            try (FileOutputStream dest = new FileOutputStream(outputFile)) {
              ByteStreams.copy(zipIn, dest);
            }
          }
        }
      }
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to unpack " + classJarOutFile
          + " to " + projectBuildOutputDirectory,
          ex);
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
