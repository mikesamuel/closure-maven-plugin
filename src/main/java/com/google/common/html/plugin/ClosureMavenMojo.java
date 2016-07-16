package com.google.common.html.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.css.OutputRenamingMapFormat;
import com.google.common.html.plugin.common.CommonPlanner;
import com.google.common.html.plugin.css.CssOptions;
import com.google.common.html.plugin.css.CssPlanner;
import com.google.common.html.plugin.plan.HashStore;
import com.google.common.html.plugin.plan.Plan;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

/**
 *
 */
@Mojo(name="generate-sources", defaultPhase=LifecyclePhase.PROCESS_SOURCES)
public class ClosureMavenMojo
extends AbstractMojo {
  @Parameter(
      defaultValue="${project.build.directory}",
      property="outputDir",
      required=true)
  private File outputDir;

  @Parameter(defaultValue="${project}", readonly=true, required=true)
  private MavenProject project;


  @Parameter(
      defaultValue="${project.basedir}/src/main/css",
      readonly=true, required=true)
  private File defaultCssSource;

  @Parameter(
      defaultValue="${project.basedir}/src/main/js",
      readonly=true, required=true)
  private File defaultJsSource;

  @Parameter(
      defaultValue="${project.basedir}/src/test/js",
      readonly=true, required=true)
  private File defaultJsTestSource;

  @Parameter(
      defaultValue="${project.basedir}/src/extern/js",
      readonly=true, required=true)
  private File defaultJsExterns;

  @Parameter(
      defaultValue="${project.build.directory}/src/main/js",
      readonly=true, required=true)
  private File defaultJsGenfiles;

  @Parameter(
      defaultValue="${project.build.directory}/src/test/js",
      readonly=true, required=true)
  private File defaultJsTestGenfiles;

  @Parameter(
      defaultValue="${project.basedir}/src/main/proto",
      readonly=true, required=true)
  private File defaultProtoSource;

  @Parameter(
      defaultValue="${project.basedir}/src/test/proto",
      readonly=true, required=true)
  private File defaultProtoTestSource;

  @Parameter(
      defaultValue="${project.basedir}/src/main/soy",
      readonly=true, required=true)
  private File defaultSoySource;

  /**
   * Options for the closure-stylesheets compiler.
   * May be specified multiple times to generate different variants, for example
   * one stylesheet for left-to-right languages like English and one for
   * right-to-left languages like Arabic.
   */
  @Parameter(property="css")
  public CssOptions[] css;

  /**
   * Options for the closure-compiler.
   * May be specified multiple times to generate different variants.
   */
  @Parameter(required=true, property="js")
  public JsOptions[] js;

  /**
   * Options for the protocol buffer compiler.
   */
  @Parameter(required=true, property="proto")
  public ProtoOptions proto;

  /**
   * Options for the Closure templates (aka Soy) compiler.
   */
  @Parameter(
      defaultValue="${project.basedir}/src/main/soy",
      property="soySource",
      required=false)
  private File[] soySource;

  // TODO: look for something under ${project.compileSourceRoots} that is
  // also under project.build.directory.
  @Parameter(
      defaultValue="${project.build.directory}/src/main/java",
      property="javaGenfiles",
      required=true)
  private File javaGenfiles;

  @Parameter(
      defaultValue="${project.build.directory}/src/test/java",
      property="javaTestGenfiles",
      required=true)
  private File javaTestGenfiles;

  @Parameter(
      defaultValue="${project.build.directory}/css/{reldir}/compiled{-basename}{-orient}.css",
      readonly=true,
      required=true)
  private String defaultCssOutputPathTemplate;

  /**
   * The output from the CSS class renaming. Provides a map of class
   * names to what they were renammed to.
   * Defaults to target/css/{reldir}/rename-map{-basename}{-orient}.json
   */
  @Parameter(
      defaultValue="${project.build.directory}/css/css-rename-map.json",
      readonly=true,
      required=true)
  private File cssRenameMap;

  @Parameter(
      defaultValue="${project.build.directory}/css/{reldir}/source-map{-basename}{-orient}.json",
      readonly=true,
      required=true)
  private String defaultCssSourceMapPathTemplate;

  @Parameter(
      defaultValue="${project.build.directory}/closure-maven-plugin-hash-store.json",
      property="hashStoreFile",
      required=true)
  private File hashStoreFile;


  public void execute() throws MojoExecutionException {
    if (!outputDir.exists()) {
      outputDir.mkdirs();
    }

    Log log = this.getLog();

    Sources protoSources;
    Sources soySources;

    ClosureMavenPluginSubstitutionMapProvider substitutionMapProvider;
    try {
      substitutionMapProvider = new ClosureMavenPluginSubstitutionMapProvider(
          Files.asByteSource(cssRenameMap).asCharSource(Charsets.UTF_8));
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to read CSS rename map " + cssRenameMap, ex);
    }

    try {
      protoSources = new Sources.Finder(".proto")
          .mainRoots(orDefault(proto.source, defaultProtoSource))
          .testRoots(orDefault(proto.testSource, defaultProtoTestSource))
          .scan(log);

      soySources = new Sources.Finder(".proto")
          .mainRoots(orDefault(soySource, defaultSoySource))
          .scan(log);
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to enumerate closure source files", ex);
    }

    File jsGenfiles = defaultJsGenfiles;
    File jsTestGenfiles = defaultJsTestGenfiles;
    if (js != null && js.length != 0) {
      JsOptions js0 = js[0];
      if (js0.jsGenfiles != null) {
        jsGenfiles = js0.jsGenfiles;
      }
      if (js0.jsTestGenfiles != null) {
        jsTestGenfiles = js0.jsTestGenfiles;
      }
    }

    HashStore hashStore = null;
    if (hashStoreFile.exists()) {
      try {
        InputStream hashStoreIn = new FileInputStream(hashStoreFile);
        try {
          Reader hashStoreReader = new InputStreamReader(
              hashStoreIn, Charsets.UTF_8);
          try {
            hashStore = HashStore.read(hashStoreReader, log);
          } finally {
            hashStoreReader.close();
          }
        } finally {
          hashStoreIn.close();
        }
      } catch (IOException ex) {
        log.warn("Failed to read hash store", ex);
      }
    }
    if (hashStore == null) {
      log.debug("Creating empty hash store");
      hashStore = new HashStore();
    }

    CommonPlanner planner = new CommonPlanner(
        log, outputDir, substitutionMapProvider, hashStore);
    try {
      new CssPlanner(planner)
          .cssRenameMap(cssRenameMap)
          .defaultCssSource(defaultCssSource)
          .defaultCssOutputPathTemplate(defaultCssOutputPathTemplate)
          .defaultCssSourceMapPathTemplate(defaultCssSourceMapPathTemplate)
          .plan(css);
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to plan CSS compile", ex);
    }

    /*
    new SoyCompilerWrapper()
        .soySources(soySources)
        .protoSources(protoSources)
        .destination(orDefault(jsGenfiles, defaultJsGenfiles))
        .compileToJs();

    new SoyCompilerWrapper()
        .soySources(soySources)
        .protoSources(protoSources)
        .destination(javaGenfiles)
        .compileToJava();

    new ProtoCompilerWrapper(proto)
        .mainRoots(protoSources)
        .destination(orDefault(jsGenfiles, defaultJsGenfiles))
        .testDestination(orDefault(jsTestGenfiles, defaultJsTestGenfiles))
        .compileToJs();

    new ProtoCompilerWrapper(proto)
        .mainRoots(protoSources)
        .destination(javaGenfiles)
        .testDestination(javaTestGenfiles)
        .compileToJava();

    Sources jsSources;
    Sources jsExterns;

    jsSources = new Sources.Finder(".js")
        .mainRoots(orDefault(js.source, defaultJsSource))
        .testRoots(orDefault(js.testSource, defaultJsTestSource))
        .mainRoots(orDefault(js.jsGenfiles, defaultJsGenfiles))
        .testRoots(orDefault(js.jsTestGenfiles, defaultJsTestGenfiles))
        .scan(log);

    jsExterns = new Sources.Finder(".js")
        .mainRoots(orDefault(js.externSource, defaultJsExterns))
        .scan(log);

    CompilerOptions jsOpts = js.toOptions(log, outputDir);

    new JsCompilerWrapper(jsOpts)
        .sources(jsSources)
        .externs(jsExterns)
        .destination(outputDir)
        .compile(log);
    */

    Plan plan = planner.toPlan();
    log.debug("Finished plan.  Executing plan");
    while (!plan.isComplete()) {
      plan.executeOneStep();
    }

    log.debug("Writing hash store to " + hashStoreFile);
    try {
      hashStoreFile.getParentFile().mkdirs();
      OutputStream hashStoreOut = new FileOutputStream(hashStoreFile);
      try {
        Writer hashStoreWriter = new OutputStreamWriter(
            hashStoreOut, Charsets.UTF_8);
        try {
          hashStore.write(hashStoreWriter);
        } finally {
          hashStoreWriter.close();
        }
      } finally {
        hashStoreOut.close();
      }
    } catch (IOException ex) {
      log.warn("Problem writing hash store", ex);
    }

    log.debug("Writing rename map to " + cssRenameMap);
    try {
      cssRenameMap.getParentFile().mkdirs();
      Writer cssRenameOut = Files.asCharSink(cssRenameMap, Charsets.UTF_8)
          .openBufferedStream();
      try {
        substitutionMapProvider.get()
             .write(OutputRenamingMapFormat.JSON, cssRenameOut);
      } finally {
        cssRenameOut.close();
      }
    } catch (IOException ex) {
      log.warn("Problem writing CSS rename map", ex);
    }
  }

  private static ImmutableList<File> orDefault(File[] files, File defaultFile) {
    if (files != null) {
      return ImmutableList.copyOf(files);
    }
    return ImmutableList.of(defaultFile);
  }
}
