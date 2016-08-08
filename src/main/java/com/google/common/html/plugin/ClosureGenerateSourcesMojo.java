package com.google.common.html.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.html.plugin.common.CommonPlanner;
import com.google.common.html.plugin.common.GenfilesDirs;
import com.google.common.html.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.common.html.plugin.common.OptionsUtils;
import com.google.common.html.plugin.css.CssOptions;
import com.google.common.html.plugin.css.CssPlanner;
import com.google.common.html.plugin.extract.ExtractPlanner;
import com.google.common.html.plugin.extract.Extracts;
import com.google.common.html.plugin.js.JsOptions;
import com.google.common.html.plugin.js.JsPlanner;
import com.google.common.html.plugin.proto.ProtoIO;
import com.google.common.html.plugin.proto.ProtoOptions;
import com.google.common.html.plugin.proto.ProtoPlanner;
import com.google.common.html.plugin.soy.SoyOptions;
import com.google.common.html.plugin.soy.SoyPlanner;

import java.io.File;
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
public class ClosureGenerateSourcesMojo extends AbstractClosureMojo {

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
    File jsGenfiles = defaultJsGenfiles;
    File jsTestGenfiles = defaultJsTestGenfiles;
    ImmutableList<JsOptions> jsOptions = OptionsUtils.prepare(
        new Supplier<JsOptions>() {
          @Override
          public JsOptions get() {
            return new JsOptions();
          }
        },
        js != null
        ? ImmutableList.copyOf(js)
        : ImmutableList.<JsOptions>of());
    {
      for (JsOptions jsOpt : jsOptions) {
        if (jsOpt.jsGenfiles != null) {
          jsGenfiles = jsOpt.jsGenfiles;
          break;
        }
      }
      for (JsOptions jsOpt : jsOptions) {
        if (jsOpt.jsTestGenfiles != null) {
          jsTestGenfiles = jsOpt.jsTestGenfiles;
          break;
        }
      }
    }

    planner.genfiles.setStoredObject(new GenfilesDirs(
        outputDir,
        javaGenfiles, javaTestGenfiles,
        jsGenfiles, jsTestGenfiles));
    try {
      planner.genfiles.write();
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to write genfile dir list", ex);
    }

    try {
      new ExtractPlanner(planner, project, pluginDescriptor)
          .plan(extracts != null ? extracts : new Extracts());
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to plan source file extraction", ex);
    }

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
    new SoyPlanner(LifecyclePhase.PROCESS_SOURCES, planner, protoIO)
        .defaultSoySource(defaultSoySource)
        .plan(soyOptions);

    new JsPlanner(planner)
        .defaultJsSource(defaultJsSource)
        .defaultJsTestSource(defaultJsTestSource)
        .plan(jsOptions);
    // TODO: figure out how to thread externs through.
    // TODO: figure out how to make the rename map available.
    // TODO: figure out how to package compiled CSS and JS.
  }
}
