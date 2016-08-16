package com.google.common.html.plugin.soy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
import com.google.common.html.plugin.common.Ingredients.HashedInMemory;
import com.google.common.html.plugin.common.Ingredients.PathValue;
import com.google.common.html.plugin.common.Ingredients.UriValue;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.PlanKey;
import com.google.common.html.plugin.plan.Step;
import com.google.common.html.plugin.plan.StepSource;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteStreams;
import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.SoyToJbcSrcCompiler;

final class SoyToJava extends Step {
  private final ReflectionableOperation<Void, SoyFileSet> makeSoyFileSet;

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
      Bundle<UriValue> protobufClassPath,
      PathValue outputJar,
      PathValue projectBuildOutputDirectory,
      ReflectionableOperation<Void, SoyFileSet> makeSoyFileSet) {
    super(
        PlanKey.builder("soy-to-java").addInp(
            options, soySources, protoDescriptors, protobufClassPath,
            outputJar, projectBuildOutputDirectory)
            .build(),
        ImmutableList.<Ingredient>of(
            options,
            soySources,
            protoDescriptors,
            protobufClassPath,
            outputJar,
            projectBuildOutputDirectory),
        Sets.immutableEnumSet(
            StepSource.PROTO_DESCRIPTOR_SET,
            StepSource.SOY_SRC, StepSource.SOY_GENERATED),
        Sets.immutableEnumSet(StepSource.JS_GENERATED));
    this.makeSoyFileSet = makeSoyFileSet;
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
    PathValue projectBuildOutputDirectoryValue = (PathValue) inputs.get(5);

    final File classJarOutFile = outputJarPath.value;
    final File srcJarOutFile = new File(
        outputJarPath.value.getParentFile(),
        FilenameUtils.removeExtension(
            outputJarPath.value.getName()) + "-src.jar");

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

    // We do not use the plugin class realm as the class loader parent
    // so that the version of Soy we use is loaded by this class
    // loader, and the Class.forName calls that the jbcsrc backend
    // uses to resolve generated protobuf message classes are in a
    // classloader that includes the target directory which by the
    // process-classes phase should include the compiled result of the
    // RunProtoc-generated java files.
    URL[] classPath = protobufClassPathUrls.build().toArray(new URL[0]);
    ClassLoader parentClassLoader = ClassLoader.getSystemClassLoader();

    class CompileToJar
    implements ReflectionableOperation<SoyFileSet, Void> {

      @Override
      public Void direct(SoyFileSet sfs) throws MojoExecutionException {
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
        return null;
      }

      @Override
      public Object reflect(ClassLoader cl, Object sfs)
      throws MojoExecutionException, ReflectiveOperationException {
        Class<?> optionalClass = cl.loadClass(Optional.class.getName());
        Class<?> byteSinkClass = cl.loadClass(ByteSink.class.getName());
        Class<?> filesClass = cl.loadClass(Files.class.getName());
        Class<?> fileWriteModeClass = cl.loadClass(
            FileWriteMode.class.getName());

        Object writeModes = Array.newInstance(fileWriteModeClass, 0);
        Method asByteSink = filesClass.getMethod(
            "asByteSink", File.class, writeModes.getClass());
        Object classJarOut = asByteSink.invoke(
            null, classJarOutFile, writeModes);

        Method optionalOf = optionalClass.getMethod("of", Object.class);
        Object srcJarOut = optionalOf.invoke(
            null, asByteSink.invoke(null, srcJarOutFile, writeModes));

        Class<?> soyFileSetClass = cl.loadClass(
            SoyFileSet.class.getName());
        Class<?> soyToJbcsrcCompilerClass = cl.loadClass(
            SoyToJbcSrcCompiler.class.getName());
        Method compile = soyToJbcsrcCompilerClass.getMethod(
            "compile", soyFileSetClass, byteSinkClass, optionalClass);
        compile.invoke(null, sfs, classJarOut, srcJarOut);
        return null;
      }

      @Override
      public String logDescription() {
        return getClass().getSimpleName();
      }
    }

    try {
      try (URLClassLoader protobufClassLoader = new URLClassLoader(
              classPath, parentClassLoader)) {
        ReflectionableOperation.Util.reflect(
            protobufClassLoader,
            ReflectionableOperation.Util.chain(
                makeSoyFileSet, new CompileToJar()),
            null);
      }
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to compile soy to JAR", ex);
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
