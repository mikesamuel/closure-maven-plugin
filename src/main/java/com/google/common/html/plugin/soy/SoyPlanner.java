package com.google.common.html.plugin.soy;

import java.io.File;
import java.net.URI;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.html.plugin.common.CommonPlanner;
import com.google.common.html.plugin.common.DirectoryScannerSpec;
import com.google.common.html.plugin.common.GenfilesDirs;
import com.google.common.html.plugin.common.Ingredients;
import com.google.common.html.plugin.common.Ingredients.Bundle;
import com.google.common.html.plugin.common.Ingredients
    .DirScanFileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.HashedInMemory;
import com.google.common.html.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.common.html.plugin.common.Ingredients.UriValue;
import com.google.common.html.plugin.common.OptionsUtils;
import com.google.common.html.plugin.proto.ProtoIO;

/**
 * Adds steps related to Soy template compilation.
 */
public final class SoyPlanner {
  private final CommonPlanner planner;
  private final SerializedObjectIngredient<ProtoIO> protoIO;
  private final LifecyclePhase phase;
  private Optional<File> defaultSoySource = Optional.absent();

  /** */
  public SoyPlanner(
      LifecyclePhase phase,
      CommonPlanner planner,
      SerializedObjectIngredient<ProtoIO> protoIO) {
    this.planner = planner;
    this.protoIO = protoIO;
    this.phase = phase;
  }

  /** Sets the default soy source root. */
  public SoyPlanner defaultSoySource(File d) {
    this.defaultSoySource = Optional.of(d);
    return this;
  }

  /** Adds steps to the common planner to compiler soy. */
  public void plan(SoyOptions soyOpts) throws MojoExecutionException {
    SoyOptions opts = OptionsUtils.prepareOne(soyOpts);
    Ingredients ingredients = planner.ingredients;

    HashedInMemory<SoyOptions> soyOptions = ingredients.hashedInMemory(
        SoyOptions.class, opts);
    SerializedObjectIngredient<GenfilesDirs> genfiles = planner.genfiles;

    File defaultSoyTestSource = new File(
        new File(new File(planner.baseDir, "src"), "test"), "soy");

    DirectoryScannerSpec dsSpec = opts.toDirectoryScannerSpec(
        defaultSoySource.get(), defaultSoyTestSource,
        planner.genfiles.getStoredObject().get());

    DirScanFileSetIngredient soySources = ingredients.fileset(dsSpec);

    ImmutableList.Builder<UriValue> runtimeClassPathElements =
        ImmutableList.builder();
    for (URI el : planner.runtimeClassPath) {
      runtimeClassPathElements.add(ingredients.uriValue(el));
    }
    Bundle<UriValue> runtimeClassPath = ingredients.bundle(
        runtimeClassPathElements.build());

    planner.addStep(new BuildSoyFileSet(
        ingredients, phase,
        genfiles, soyOptions, soySources, protoIO,
        runtimeClassPath,
        ingredients.pathValue(planner.outputDir),
        planner.projectBuildOutputDirectory));
  }
}
