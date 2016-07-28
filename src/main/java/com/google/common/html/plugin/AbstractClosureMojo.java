package com.google.common.html.plugin;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.google.common.html.plugin.css.CssOptions;
import com.google.common.html.plugin.extract.Extract;
import com.google.common.html.plugin.js.JsOptions;
import com.google.common.html.plugin.proto.ProtoOptions;
import com.google.common.html.plugin.soy.SoyOptions;

abstract class AbstractClosureMojo extends AbstractMojo {
  @Parameter(
      defaultValue="${project.build.directory}",
      property="outputDir",
      required=true,
      readonly=true)
  protected File outputDir;

  @Parameter(defaultValue="${project}", readonly=true, required=true)
  protected MavenProject project;


  @Parameter(
      defaultValue="${project.basedir}/src/main/css",
      readonly=true, required=true)
  protected File defaultCssSource;

  @Parameter(
      defaultValue="${project.basedir}/src/main/js",
      readonly=true, required=true)
  protected File defaultJsSource;

  @Parameter(
      defaultValue="${project.basedir}/src/test/js",
      readonly=true, required=true)
  protected File defaultJsTestSource;

  @Parameter(
      defaultValue="${project.basedir}/src/extern/js",
      readonly=true, required=true)
  protected File defaultJsExterns;

  @Parameter(
      defaultValue="${project.build.directory}/src/main/js",
      readonly=true, required=true)
  protected File defaultJsGenfiles;

  @Parameter(
      defaultValue="${project.build.directory}/src/test/js",
      readonly=true, required=true)
  protected File defaultJsTestGenfiles;

  @Parameter(
      defaultValue="${project.basedir}/src/main/proto",
      readonly=true, required=true)
  protected File defaultProtoSource;

  @Parameter(
      defaultValue="${project.basedir}/src/test/proto",
      readonly=true, required=true)
  protected File defaultProtoTestSource;

  @Parameter(
      defaultValue="${project.basedir}/src/main/soy",
      readonly=true, required=true)
  protected File defaultSoySource;

  /**
   * The dependencies from which to extract supplementary source files.
   */
  @Parameter
  protected Extract[] extracts;

  /**
   * Options for the closure-stylesheets compiler.
   * May be specified multiple times to generate different variants, for example
   * one stylesheet for left-to-right languages like English and one for
   * right-to-left languages like Arabic.
   */
  @Parameter
  public CssOptions[] css;

  /**
   * Options for the closure-compiler.
   * May be specified multiple times to generate different variants.
   */
  @Parameter
  public JsOptions[] js;

  /**
   * Options for the protocol buffer compiler.
   */
  @Parameter
  public ProtoOptions proto;

  /**
   * Options for the closure template compiler.
   */
  @Parameter
  public SoyOptions soy;

  // TODO: look for something under ${project.compileSourceRoots} that is
  // also under project.build.directory.
  /** The source root for generated {@code .java} files. */
  @Parameter(
      defaultValue="${project.build.directory}/src/main/java",
      property="javaGenfiles",
      required=true)
  protected File javaGenfiles;

  /** The source root for generated {@code .java} test files. */
  @Parameter(
      defaultValue="${project.build.directory}/src/test/java",
      property="javaTestGenfiles",
      required=true)
  protected File javaTestGenfiles;

  @Parameter(
      defaultValue=
          "${project.build.directory}/css/{reldir}"
          + "/compiled{-basename}{-orient}.css",
      readonly=true,
      required=true)
  protected String defaultCssOutputPathTemplate;

  /**
   * The output from the CSS class renaming. Provides a map of class
   * names to what they were renammed to.
   * Defaults to target/css/{reldir}/rename-map{-basename}{-orient}.json
   */
  @Parameter(
      defaultValue="${project.build.directory}/css/css-rename-map.json",
      readonly=true,
      required=true)
  protected File cssRenameMap;

  @Parameter(
      defaultValue=
          "${project.build.directory}/css/{reldir}"
          + "/source-map{-basename}{-orient}.json",
      readonly=true,
      required=true)
  protected String defaultCssSourceMapPathTemplate;

  /** Path to the file that stores hashes of intermediate outputs. */
  @Parameter(
      defaultValue=
          "${project.build.directory}/closure-maven-plugin-hash-store.json",
      property="hashStoreFile",
      required=true)
  protected File hashStoreFile;

  @Parameter(
      defaultValue="${project.build.directory}/src/main/proto/descriptors.pd",
      readonly=true,
      required=true)
  protected File defaultMainDescriptorFile;

  @Parameter(
      defaultValue="${project.build.directory}/src/test/proto/descriptors.pd",
      readonly=true,
      required=true)
  protected File defaultTestDescriptorFile;
}
