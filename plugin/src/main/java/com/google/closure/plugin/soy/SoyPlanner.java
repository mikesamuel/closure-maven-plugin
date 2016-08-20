package com.google.closure.plugin.soy;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Optional;
import com.google.closure.plugin.common.CommonPlanner;
import com.google.closure.plugin.common.DirectoryScannerSpec;
import com.google.closure.plugin.common.GenfilesDirs;
import com.google.closure.plugin.common.Ingredients;
import com.google.closure.plugin.common.Ingredients
    .DirScanFileSetIngredient;
import com.google.closure.plugin.common.Ingredients.HashedInMemory;
import com.google.closure.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.closure.plugin.common.OptionsUtils;
import com.google.closure.plugin.proto.ProtoIO;

/**
 * Adds steps related to Soy template compilation.
 */
public final class SoyPlanner {
  private final CommonPlanner planner;
  private final SerializedObjectIngredient<ProtoIO> protoIO;
  private Optional<File> defaultSoySource = Optional.absent();

  /** */
  public SoyPlanner(
      CommonPlanner planner,
      SerializedObjectIngredient<ProtoIO> protoIO) {
    this.planner = planner;
    this.protoIO = protoIO;
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
    HashedInMemory<GenfilesDirs> genfiles = planner.genfiles;

    File defaultSoyTestSource = new File(
        new File(new File(planner.baseDir, "src"), "test"), "soy");

    DirectoryScannerSpec dsSpec = opts.toDirectoryScannerSpec(
        defaultSoySource.get(), defaultSoyTestSource,
        genfiles.getValue());

    DirScanFileSetIngredient soySources = ingredients.fileset(dsSpec);

    planner.addStep(new BuildSoyFileSet(
        ingredients,
        genfiles, soyOptions, soySources, protoIO,
        ingredients.pathValue(planner.outputDir),
        planner.projectBuildOutputDirectory));
  }
}
