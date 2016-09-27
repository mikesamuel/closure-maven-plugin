package com.google.closure.plugin.plan;

import java.io.File;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.sonatype.plexus.build.incremental.BuildContext;

import com.google.closure.plugin.common.FileExt;
import com.google.closure.plugin.common.GenfilesDirs;
import com.google.closure.plugin.common.ProcessRunner;
import com.google.closure.plugin.common.SrcfilesDirs;
import com.google.closure.plugin.common.StableCssSubstitutionMapProvider;
import com.google.closure.plugin.proto.ProtoIO;
import com.google.common.collect.ImmutableList;

/** Common context that might change from build to build. */
public final class PlanContext {
  /** Used to invoke external compilers. */
  public final ProcessRunner processRunner;
  /** Describes the build plugin which bundles critical dependencies. */
  public final PluginDescriptor pluginDescriptor;
  /** Determines which files need to be rebuilt. */
  public final BuildContext buildContext;
  /** Sink for informational messages. */
  public final Log log;
  /** Locations of source root directories. */
  public final SrcfilesDirs srcfilesDirs;
  /** Locations of generated source directories. */
  public final GenfilesDirs genfilesDirs;
  /** Project dependencies and the plugin artifact. */
  public final ImmutableList<Artifact> artifacts;
  /** The maven {@code target} directory. */
  public final File outputDir;
  /** The maven {@code target/classes} directory. */
  public final File projectBuildOutputDirectory;
  /** {@link #outputDir}{@code /classes/closure}. */
  public final File closureOutputDirectory;
  /** The common CSS identifier substitution map provider. */
  public final StableCssSubstitutionMapProvider substitutionMapProvider;
  /**
   * Communicates location of protoc output files
   * to generated proto message consumers.
   */
  public final ProtoIO protoIO = new ProtoIO();

  /** */
  public PlanContext(
      ProcessRunner processRunner,
      PluginDescriptor pluginDescriptor,
      BuildContext buildContext,
      Log log,
      SrcfilesDirs srcfilesDirs,
      GenfilesDirs genfilesDirs,
      ImmutableList<Artifact> artifacts,
      File outputDir,
      File projectBuildOutputDirectory,
      File closureOutputDirectory,
      StableCssSubstitutionMapProvider substitutionMapProvider) {
    this.processRunner = processRunner;
    this.pluginDescriptor = pluginDescriptor;
    this.buildContext = buildContext;
    this.log = log;
    this.srcfilesDirs = srcfilesDirs;
    this.genfilesDirs = genfilesDirs;
    this.artifacts = artifacts;
    this.outputDir = outputDir;
    this.projectBuildOutputDirectory = projectBuildOutputDirectory;
    this.closureOutputDirectory = closureOutputDirectory;
    this.substitutionMapProvider = substitutionMapProvider;
  }

  /** The output directory for files with the given extension. */
  public File closureOutputDirectoryForExt(FileExt extension) {
    return new File(closureOutputDirectory, extension.extension);
  }
}
