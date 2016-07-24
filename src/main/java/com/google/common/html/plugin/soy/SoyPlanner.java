package com.google.common.html.plugin.soy;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Optional;
import com.google.common.html.plugin.Sources;
import com.google.common.html.plugin.common.CommonPlanner;
import com.google.common.html.plugin.common.GenfilesDirs;
import com.google.common.html.plugin.common.Ingredients.FileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients.PathValue;
import com.google.common.html.plugin.common.Ingredients.SerializedObjectIngredient;

/**
 * Adds steps related to Soy template compilation.
 */
public final class SoyPlanner {
  private final CommonPlanner planner;
  private Optional<File> defaultSoySource = Optional.absent();

  public SoyPlanner(CommonPlanner planner) {
    this.planner = planner;
  }

  public SoyPlanner defaultSoySource(File d) {
    this.defaultSoySource = Optional.of(d);
    return this;
  }

  public void plan(SoyOptions opts) throws MojoExecutionException, IOException {
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

    planner.addStep(new SoyToJava(
        soyOptions, soySources, outputJar));

    planner.addStep(new SoyToJs(
        soyOptions, soySources, planner.ingredients.pathValue(gd.jsGenfiles)));
  }

}
