package com.google.common.html.plugin.css;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.html.plugin.common.CommonPlanner;
import com.google.common.html.plugin.common.OptionsUtils;
import com.google.common.html.plugin.common.Ingredients.HashedInMemory;
import com.google.common.html.plugin.common.Ingredients
    .SerializedObjectIngredient;

/**
 * Builds a plan that scans for CSS source files, and invokes the
 * closure-stylesheets compiler as necessary.
 */
public final class CssPlanner {

  final CommonPlanner planner;
  private File cssRenameMap;
  private File defaultCssSource;
  private String defaultCssOutputPathTemplate;
  private String defaultCssSourceMapPathTemplate;

  /** */
  public CssPlanner(CommonPlanner planner) {
    this.planner = planner;
  }

  /**
   * @param f path to a file with the CSS rename map used to map IDs/CLASSes to
   *     shorter names.
   */
  public CssPlanner cssRenameMap(File f) {
    this.cssRenameMap = Preconditions.checkNotNull(f);
    return this;
  }

  /**
   * @return path to a file with the CSS rename map used to map IDs/CLASSes to
   *     shorter names.
   */
  public File cssRenameMap() {
    return this.cssRenameMap;
  }

  /** @param f a default css source search path. */
  public CssPlanner defaultCssSource(File f) {
    this.defaultCssSource = Preconditions.checkNotNull(f);
    return this;
  }

  /** @return a default css source search path. */
  public File defaultCssSource() {
    return this.defaultCssSource;
  }

  /** @param t a default path template for the compiled css. */
  public CssPlanner defaultCssOutputPathTemplate(String t) {
    this.defaultCssOutputPathTemplate = Preconditions.checkNotNull(t);
    return this;
  }

  /** @return a default path template for the compiled css. */
  public String defaultCssOutputPathTemplate() {
    return this.defaultCssOutputPathTemplate;
  }

  /** @param t a default path template for the source line mapping. */
  public CssPlanner defaultCssSourceMapPathTemplate(String t) {
    this.defaultCssSourceMapPathTemplate = Preconditions.checkNotNull(t);
    return this;
  }

  /** @return a default path template for the source line mapping. */
  public String defaultCssSourceMapPathTemplate() {
    return this.defaultCssSourceMapPathTemplate;
  }

  File cssOutputDir() {
    return new File(planner.outputDir, "css");
   }


  private
  ImmutableList<HashedInMemory<CssOptions>> optionsIngredients(
      CssOptions[] options)
  throws MojoExecutionException {
    // Multiple the options out so that there is at most one output
    // orientation and vendor per option.
    ImmutableList<CssOptions> cssOptionSets = OptionsUtils.prepare(
        new Supplier<CssOptions>() {
          @Override
          public CssOptions get() {
            return new CssOptions();
          }
        },
        options != null
        ? ImmutableList.copyOf(options)
        : ImmutableList.<CssOptions>of());
    ImmutableList.Builder<HashedInMemory<CssOptions>> b =
        ImmutableList.builder();
    for (CssOptions o : cssOptionSets) {
      b.add(planner.ingredients.hashedInMemory(CssOptions.class, o));
    }
    return b.build();
  }


  /**
   * Adds steps related to CSS compilation to the master plan.
   */
  public void plan(CssOptions[] css)
  throws IOException, MojoExecutionException {
    Preconditions.checkNotNull(cssRenameMap);
    Preconditions.checkNotNull(defaultCssSource);
    Preconditions.checkNotNull(defaultCssOutputPathTemplate);
    Preconditions.checkNotNull(defaultCssSourceMapPathTemplate);

    SerializedObjectIngredient<CssOptionsById> optionsListFile =
        planner.ingredients.serializedObject(
            new File(cssOutputDir(), "css-options.ser"),
            CssOptionsById.class);

    planner.addStep(
        new ListOptions(
            this, optionsIngredients(css), planner.genfiles, optionsListFile));
  }
}
