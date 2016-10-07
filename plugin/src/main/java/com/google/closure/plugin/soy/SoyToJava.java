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

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.closure.plugin.plan.BundlingPlanGraphNode.OptionsAndBundles;
import com.google.closure.plugin.plan.CompilePlanGraphNode;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;
import com.google.closure.plugin.plan.Update;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteStreams;
import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.SoyToJbcSrcCompiler;

final class SoyToJava extends CompilePlanGraphNode<SoyOptions, SoyBundle> {

  SoyToJava(PlanContext context) {
    super(context);
  }

  private static File getSrcJarPath(SoyBundle b) {
    return new File(
        b.outputJar.getParentFile(),
        FilenameUtils.removeExtension(
            b.outputJar.getName()) + "-src.jar");
  }

  @Override
  protected void process() throws IOException, MojoExecutionException {
    processDefunctBundles(optionsAndBundles);

    Update<OptionsAndBundles<SoyOptions, SoyBundle>> u =
        optionsAndBundles.get();

    this.changedFiles.clear();
    for (OptionsAndBundles<SoyOptions, SoyBundle> c : u.changed) {
      for (SoyBundle b : c.bundles) {
        processOne(b);
      }
    }
  }

  protected void processOne(SoyBundle bundle)
  throws MojoExecutionException {
    final File classJarOutFile = bundle.outputJar;
    final File srcJarOutFile = getSrcJarPath(bundle);

    SoyFileSet sfs = bundle.sfsSupplier.getSoyFileSet(context);

    // Compile To Jar
    final FileWriteMode[] writeModes = new FileWriteMode[0];
    ByteSink classJarOut = Files.asByteSink(classJarOutFile, writeModes);
    Optional<ByteSink> srcJarOut = Optional.of(
        Files.asByteSink(srcJarOutFile, writeModes));
    try {
      // TODO: relay errors and warnings via build context.
      SoyToJbcSrcCompiler.compile(sfs, classJarOut, srcJarOut);
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to write compiled Soy output to a JAR", ex);
    }

    ImmutableList.Builder<File> outputsThisBundleBuilder =
        ImmutableList.builder();
    outputsThisBundleBuilder.add(classJarOutFile).add(srcJarOutFile);

    // Unpack JAR into classes directory.
    File projectBuildOutputDirectory = context.projectBuildOutputDirectory;
    try {
      try (InputStream in = new FileInputStream(classJarOutFile)) {
        try (ZipInputStream zipIn = new ZipInputStream(in)) {
          for (ZipEntry entry; (entry = zipIn.getNextEntry()) != null;
              zipIn.closeEntry()) {
            if (entry.isDirectory()) {
              continue;
            }
            String name = Files.simplifyPath(
                entry.getName().replace('/', File.separatorChar));
            if (name.startsWith("META-INF")) { continue; }
            context.log.debug("Unpacking " + name + " from soy generated jar");
            File outputFile = new File(FilenameUtils.concat(
                projectBuildOutputDirectory.getPath(), name));
            Files.createParentDirs(outputFile);
            outputsThisBundleBuilder.add(outputFile);
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

    ImmutableList<File> outputsThisBundle = outputsThisBundleBuilder.build();
    this.bundleToOutputs.put(bundle, outputsThisBundle);
    // TODO: We could hash before compiling to filter this down.
    this.changedFiles.addAll(outputsThisBundle);
  }

  @Override
  protected SV getStateVector() {
    return new SV(this);
  }

  static final class SV
  extends CompilePlanGraphNode.CompileStateVector<SoyOptions, SoyBundle> {

    private static final long serialVersionUID = 1L;

    protected SV(SoyToJava node) {
      super(node);
    }

    @Override
    public PlanGraphNode<?> reconstitute(PlanContext context, JoinNodes jn) {
      SoyToJava node = apply(new SoyToJava(context));
      BuildSoyFileSet.initSfss(node.optionsAndBundles, context);
      return node;
    }
  }
}
