package com.google.common.html.plugin;

import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import com.google.common.html.plugin.common.CommonPlanner;
import com.google.common.html.plugin.common.Ingredients.SerializedObjectIngredient;
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
    // Required because ProtocBundledMojo requires dependency resolution
    // so it can figure out which protobufVersion to use.
    requiresDependencyResolution=ResolutionScope.COMPILE_PLUS_RUNTIME
)
public final class ClosureCompileMojo extends AbstractClosureMojo {

  @Override
  protected void formulatePlan(CommonPlanner planner) throws MojoExecutionException {
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
  }
}
