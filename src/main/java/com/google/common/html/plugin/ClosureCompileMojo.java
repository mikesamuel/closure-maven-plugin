package com.google.common.html.plugin;

import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.html.plugin.common.CommonPlanner;
import com.google.common.html.plugin.common.Ingredients;
import com.google.common.html.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.common.html.plugin.common.Ingredients
    .SettableFileSetIngredient;
import com.google.common.html.plugin.common.ToolFinder;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.PlanKey;
import com.google.common.html.plugin.plan.Step;
import com.google.common.html.plugin.plan.StepSource;
import com.google.common.html.plugin.proto.ProtoIO;
import com.google.common.html.plugin.proto.ProtoPlanner;
import com.google.common.html.plugin.soy.SoyOptions;
import com.google.common.html.plugin.soy.SoyPlanner;


/**
 * Generates .class files from .soy inputs using the .java sources generated
 * from .proto files during the process-sources phase.
 */
@Mojo(
    name="compile-closure",
    defaultPhase=LifecyclePhase.PROCESS_CLASSES,
    requiresDependencyResolution=ResolutionScope.COMPILE_PLUS_RUNTIME
)
public final class ClosureCompileMojo extends AbstractClosureMojo {

  @Override
  protected void formulatePlan(CommonPlanner planner)
  throws MojoExecutionException {
    try {
      planner.genfiles.read();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to load genfiles", ex);
    }

    SerializedObjectIngredient<ProtoIO> protoIO;
    try {
      ProtoPlanner pp = new ProtoPlanner(planner, protocExecutable())
          .defaultProtoSource(defaultProtoSource)
          .defaultProtoTestSource(defaultProtoTestSource)
          .defaultMainDescriptorFile(defaultMainDescriptorFile)
          .defaultTestDescriptorFile(defaultTestDescriptorFile);
      protoIO = pp.getProtoIO();
      // We don't actually plan any proto steps in this phase.
      protoIO.read();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to plan proto compile", ex);
    }

    SoyOptions soyOptions = soy != null ? soy : new SoyOptions();
    new SoyPlanner(LifecyclePhase.PROCESS_CLASSES, planner, protoIO)
        .defaultSoySource(defaultSoySource)
        .plan(soyOptions);

    planner.addStep(new FindSoy2Java(
        planner.ingredients, planner.soy2JavaJar, this.getSoyToJavaCompiler()));
  }
}


final class FindSoy2Java extends Step {
  final Ingredients ingredients;
  final SettableFileSetIngredient soy2JavaJar;
  final ToolFinder<?> soyToJavaCompilerFinder;

  public FindSoy2Java(
      Ingredients ingredients,
      SettableFileSetIngredient soy2JavaJar,
      ToolFinder<?> soyToJavaCompilerFinder) {
    super(
        PlanKey.builder("find-soy2java").build(),
        ImmutableList.<Ingredient>of(),
        ImmutableSet.<StepSource>of(),
        Sets.immutableEnumSet(StepSource.SOY_TO_JAVA_COMPILER));
    this.ingredients = ingredients;
    this.soy2JavaJar = soy2JavaJar;
    this.soyToJavaCompilerFinder = soyToJavaCompilerFinder;
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    soyToJavaCompilerFinder.find(null, ingredients, soy2JavaJar);
  }

  @Override
  public void skip(Log log) throws MojoExecutionException {
    execute(log);
  }

  @Override
  public ImmutableList<Step> extraSteps(Log log) throws MojoExecutionException {
    return ImmutableList.of();
  }
}
