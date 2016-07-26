package com.google.common.html.plugin.soy;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Optional;
import com.google.common.html.plugin.Sources;
import com.google.common.html.plugin.common.CommonPlanner;
import com.google.common.html.plugin.common.GenfilesDirs;
import com.google.common.html.plugin.common.Ingredients.FileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients.PathValue;
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
  public void plan(SoyOptions soy) throws MojoExecutionException {
    SoyOptions opts = OptionsUtils.prepareOne(soy);

    OptionsIngredient<SoyOptions> soyOptions = planner.ingredients.options(
        SoyOptions.class, opts);
    SerializedObjectIngredient<GenfilesDirs> genfiles = planner.genfiles;

    GenfilesDirs gd = genfiles.getStoredObject().get();

    Sources.Finder soySourceFinder = new Sources.Finder(".soy");
    if (opts.source != null && opts.source.length != 0) {
      soySourceFinder.mainRoots(opts.source);
    } else {
      soySourceFinder.mainRoots(defaultSoySource.get());
    }
    soySourceFinder.mainRoots(
        gd.getGeneratedSourceDirectoryForExtension("soy", false));



    FileSetIngredient soySources = planner.ingredients.fileset(soySourceFinder);

    PathValue outputJar = planner.ingredients.pathValue(
        new File(
            planner.outputDir, "closure-templates-" + opts.getId() + ".jar"));

    // TODO: add a step that creates the file-set, and then spawns the two
    // other steps, so that we do not get two sets of Soy parse log messages.


    planner.addStep(new SoyToJava(
        soyOptions, soySources, protoIO, outputJar));

    planner.addStep(new SoyToJs(
        soyOptions, soySources, protoIO,
        planner.ingredients.pathValue(gd.jsGenfiles)));
  }

}
