package com.google.closure.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import com.google.closure.plugin.css.CssOptions;
import com.google.closure.plugin.css.CssPlanner;
import com.google.closure.plugin.extract.ExtractPlanner;
import com.google.closure.plugin.extract.Extracts;
import com.google.closure.plugin.genjava.GenSymbolsPlanner;
import com.google.closure.plugin.js.JsOptions;
import com.google.closure.plugin.js.JsPlanner;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraph;
import com.google.closure.plugin.proto.ProtoOptions;
import com.google.closure.plugin.proto.ProtoPlanner;
import com.google.closure.plugin.soy.SoyOptions;
import com.google.closure.plugin.soy.SoyPlanner;
import com.google.common.collect.ImmutableList;

/**
 * Generates .js and .java sources from .proto and .soy and compiles
 * .js and .css to optimized bundles.
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
  private final ImmutableList.Builder<CssOptions> css = ImmutableList.builder();

  /**
   * Options for the closure-compiler.
   * <p>
   * May be specified multiple times to generate different variants, for example
   * with different
   * <a href="https://developers.google.com/closure/compiler/docs/js-for-compiler#tag-define">{@code --define}s</a>.
   */
  @Parameter(property="js")
  private final ImmutableList.Builder<JsOptions> js =
      ImmutableList.builder();

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
  protected void formulatePlan(PlanGraph planGraph)
  throws MojoExecutionException {
    PlanContext context = planGraph.getContext();
    JoinNodes joinNodes = planGraph.getJoinNodes();

    new ExtractPlanner(context, joinNodes)
        .plan(extracts != null ? extracts : new Extracts());

    new CssPlanner(context, joinNodes)
        .defaultCssSource(defaultCssSource)
        .defaultCssOutputPathTemplate(defaultCssOutputPathTemplate)
        .defaultCssSourceMapPathTemplate(defaultCssSourceMapPathTemplate)
        .plan(css.build());

    ProtoPlanner protoPlanner = makeProtoPlanner(context, joinNodes);
    protoPlanner.plan(protoPlanner.prepare(proto));

    SoyOptions soyOptions = soy != null ? soy : new SoyOptions();
    new SoyPlanner(context, joinNodes)
        .plan(soyOptions);

    new JsPlanner(context, joinNodes)
        .plan(js.build());

    new GenSymbolsPlanner(context, joinNodes)
        .genJavaPackageName(genJavaPackageName)
        .plan();

    // TODO: figure out how to thread externs through.
  }

  /** Additive setter called by plexus configurator. */
  public void setJs(JsOptions options) {
    this.js.add(options);
  }

  /** Additive setter called by plexus configurator. */
  public void setCss(CssOptions options) {
    this.css.add(options);
  }

  @Override
  protected void initLoadedPlan(PlanGraph planGraph)
  throws MojoExecutionException {
    PlanContext context = planGraph.getContext();
    JoinNodes joinNodes = planGraph.getJoinNodes();

    makeProtoPlanner(context, joinNodes).prepare(proto);
  }

  private ProtoPlanner makeProtoPlanner(
      PlanContext context, JoinNodes joinNodes) {
  return new ProtoPlanner(context, joinNodes, protocExecutable())
      .defaultMainDescriptorFile(defaultMainDescriptorFile)
      .defaultTestDescriptorFile(defaultTestDescriptorFile);
  }
}
