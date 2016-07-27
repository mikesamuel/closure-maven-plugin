package com.google.common.html.plugin.soy;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Optional;
import com.google.common.html.plugin.Sources;
import com.google.common.html.plugin.common.CommonPlanner;
import com.google.common.html.plugin.common.GenfilesDirs;
import com.google.common.html.plugin.common.Ingredients;
import com.google.common.html.plugin.common.Ingredients
    .DirScanFileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.common.html.plugin.common.OptionsUtils;
import com.google.common.html.plugin.proto.ProtoIO;

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

    OptionsIngredient<SoyOptions> soyOptions = ingredients.options(
        SoyOptions.class, opts);
    SerializedObjectIngredient<GenfilesDirs> genfiles = planner.genfiles;

    Sources.Finder soySourceFinder = new Sources.Finder(".soy");
    if (opts.source != null && opts.source.length != 0) {
      soySourceFinder.mainRoots(opts.source);
    } else {
      soySourceFinder.mainRoots(defaultSoySource.get());
    }
    soySourceFinder.mainRoots(
        genfiles.getStoredObject().get()
        .getGeneratedSourceDirectoryForExtension("soy", false));

    DirScanFileSetIngredient soySources = ingredients.fileset(soySourceFinder);

    planner.addStep(new BuildSoyFileSet(
        ingredients,
        genfiles, soyOptions, soySources,
        protoIO, ingredients.pathValue(planner.outputDir)));
  }

}
