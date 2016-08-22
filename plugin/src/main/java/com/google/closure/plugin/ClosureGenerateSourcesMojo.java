package com.google.closure.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import com.google.common.collect.ImmutableList;
import com.google.closure.plugin.common.CommonPlanner;
import com.google.closure.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.closure.plugin.css.CssOptions;
import com.google.closure.plugin.css.CssPlanner;
import com.google.closure.plugin.extract.ExtractPlanner;
import com.google.closure.plugin.extract.Extracts;
import com.google.closure.plugin.genjava.GenSymbolsPlanner;
import com.google.closure.plugin.js.JsOptions;
import com.google.closure.plugin.js.JsPlanner;
import com.google.closure.plugin.proto.ProtoIO;
import com.google.closure.plugin.proto.ProtoOptions;
import com.google.closure.plugin.proto.ProtoPlanner;
import com.google.closure.plugin.soy.SoyOptions;
import com.google.closure.plugin.soy.SoyPlanner;

import java.io.IOException;

/**
 * Generates .js & .java sources from .proto and .soy and compiles .js and .css
 * to optimized bundles.
 */
@Mojo(
    name="generate-closure-sources",
    defaultPhase=LifecyclePhase.PROCESS_SOURCES,
    // Required because ProtocBundledMojo requires dependency resolution
    // so it can figure out which protobufVersion to use.
    requiresDependencyResolution=ResolutionScope.COMPILE_PLUS_RUNTIME
)
public final class ClosureGenerateSourcesMojo extends AbstractClosureMojo {

  /**
   * The dependencies from which to extract supplementary source files.
   */
  @Parameter
  protected Extracts extracts;

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
   * <p>
   * May be specified multiple times to generate different variants, for example
   * with different
   * <a href="https://developers.google.com/closure/compiler/docs/js-for-compiler#tag-define">{@code --define}s</a>.
   */
  @Parameter
  public JsOptions[] js;

  /**
   * Options for the protocol buffer compiler.
   */
  @Parameter
  public ProtoOptions proto;

  @Override
  public void execute() throws MojoExecutionException {
    super.execute();

    // Make sure the compile phase ends up compiling the protobuf sources.
    project.addCompileSourceRoot(this.javaGenfiles.getPath());
    project.addTestCompileSourceRoot(this.javaTestGenfiles.getPath());
  }

  @Override
  protected void formulatePlan(CommonPlanner planner)
  throws MojoExecutionException {
    try {
      new ExtractPlanner(planner, project, pluginDescriptor)
          .plan(extracts != null ? extracts : new Extracts());
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to plan source file extraction", ex);
    }

    try {
      new CssPlanner(planner)
          .cssRenameMap(planner.substitutionMapProvider.getBackingFile())
          .defaultCssSource(defaultCssSource)
          .defaultCssOutputPathTemplate(defaultCssOutputPathTemplate)
          .defaultCssSourceMapPathTemplate(defaultCssSourceMapPathTemplate)
          .plan(css);
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to plan CSS compile", ex);
    }

    SerializedObjectIngredient<ProtoIO> protoIO;
    try {
      ProtoPlanner pp = new ProtoPlanner(planner, protocExecutable())
          .defaultProtoSource(defaultProtoSource)
          .defaultProtoTestSource(defaultProtoTestSource)
          .defaultMainDescriptorFile(defaultMainDescriptorFile)
          .defaultTestDescriptorFile(defaultTestDescriptorFile);
      pp.plan(proto != null ? proto : new ProtoOptions());

      protoIO = pp.getProtoIO();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to plan proto compile", ex);
    }

    SoyOptions soyOptions = soy != null ? soy : new SoyOptions();
    new SoyPlanner(planner, protoIO)
        .defaultSoySource(defaultSoySource)
        .plan(soyOptions);

    try {
      new JsPlanner(planner)
          .defaultJsSource(defaultJsSource)
          .defaultJsTestSource(defaultJsTestSource)
          .plan(
              js != null
              ? ImmutableList.copyOf(js)
              : ImmutableList.<JsOptions>of());
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to plan js compile", ex);
    }

    new GenSymbolsPlanner(planner)
        .genJavaPackageName(genJavaPackageName)
        .plan();

    // TODO: figure out how to thread externs through.
  }
}
