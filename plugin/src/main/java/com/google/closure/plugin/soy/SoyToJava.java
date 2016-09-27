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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.closure.plugin.common.FileExt;
import com.google.closure.plugin.plan.CompilePlanGraphNode;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteStreams;
import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.SoyToJbcSrcCompiler;

final class SoyToJava extends CompilePlanGraphNode<SoyOptions, SoyBundle> {

  final File outputJarPath;

  SoyToJava(
      PlanContext context,
      SoyOptions options,
      SoyBundle bundle,
      File outputJarPath) {
    super(context, options, bundle);
    this.outputJarPath = Preconditions.checkNotNull(outputJarPath);
  }

  private File getSrcJarPath() {
    return new File(
        outputJarPath.getParentFile(),
        FilenameUtils.removeExtension(
            outputJarPath.getName()) + "-src.jar");
  }

  @Override
  protected void processInputs() throws IOException, MojoExecutionException {
    final File classJarOutFile = this.outputJarPath;
    final File srcJarOutFile = getSrcJarPath();

    bundle.sfsSupplier.init(context, options, bundle.inputs);
    SoyFileSet sfs = bundle.sfsSupplier.getSoyFileSet();

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
            String name = Files.simplifyPath(entry.getName());
            if (name.startsWith("META-INF")) { continue; }
            context.log.debug("Unpacking " + name + " from soy generated jar");
            File outputFile = new File(FilenameUtils.concat(
                projectBuildOutputDirectory.getPath(), name));
            Files.createParentDirs(outputFile);
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
    this.outputs = Optional.of(ImmutableList.of(
        outputJarPath, getSrcJarPath()));
  }

  @Override
  protected
  Optional<ImmutableList<PlanGraphNode<?>>> rebuildFollowersList(JoinNodes jn)
  throws MojoExecutionException {
    return Optional.of(jn.followersOf(FileExt.JAVA));
  }

  @Override
  protected SV getStateVector() {
    return new SV(options, bundle, outputs, outputJarPath);
  }

  static final class SV
  extends CompilePlanGraphNode.CompileStateVector<SoyOptions, SoyBundle> {

    private static final long serialVersionUID = 1L;

    final File outputJarPath;

    protected SV(
        SoyOptions options, SoyBundle bundle,
        Optional<ImmutableList<File>> outputs,
        File outputJarPath) {
      super(options, bundle, outputs);
      this.outputJarPath = outputJarPath;
    }

    @SuppressWarnings("synthetic-access")
    @Override
    public PlanGraphNode<?> reconstitute(PlanContext context, JoinNodes jn) {
      SoyToJava node = new SoyToJava(
          context, options, bundle,
          // The first output is the class JAR file and the second is the
          // source JAR file which is derived.
          outputJarPath);
      node.outputs = outputs;
      return node;
    }
  }
}
