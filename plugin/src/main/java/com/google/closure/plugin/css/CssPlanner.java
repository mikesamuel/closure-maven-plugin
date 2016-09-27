package com.google.closure.plugin.css;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.closure.plugin.common.FileExt;
import com.google.closure.plugin.common.OptionsUtils;
import com.google.closure.plugin.common.SourceOptions.SourceRootBuilder;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;

/**
 * Builds a plan that scans for CSS source files, and invokes the
 * closure-stylesheets compiler as necessary.
 */
public final class CssPlanner {

  private final PlanContext context;
  private final JoinNodes joinNodes;
  private File defaultCssSource;
  private String defaultCssOutputPathTemplate;
  private String defaultCssSourceMapPathTemplate;

  /** */
  public CssPlanner(PlanContext context, JoinNodes joinNodes) {
    this.context = context;
    this.joinNodes = joinNodes;
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

  ImmutableList<CssOptions> optionSets(
      Iterable<? extends CssOptions> options)
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
        options);
    for (CssOptions o : cssOptionSets) {
      if (o.source == null || o.source.length == 0) {
        o.source = new SourceRootBuilder[] {
          new SourceRootBuilder(),
        };
        o.source[0].set(this.defaultCssSource);
      }
      if (Strings.isNullOrEmpty(o.output)) {
        o.output = this.defaultCssOutputPathTemplate;
      }
      if (Strings.isNullOrEmpty(o.sourceMapFile)) {
        o.sourceMapFile = this.defaultCssSourceMapPathTemplate;
      }
    }
    return cssOptionSets;
  }

  /** Builds an entry point to the CSS build chain. */
  public PlanGraphNode<?> plan(Iterable<? extends CssOptions> unprepared)
  throws MojoExecutionException {
    ImmutableList<CssOptions> optionSets = optionSets(unprepared);

    ListOptions listOptionsNode = new ListOptions(context);
    listOptionsNode.setOptionSets(optionSets);

    // This pipeline takes in CSS files and produces CSS outputs along with a
    // JSON and rename map.
    joinNodes.pipeline(
        ImmutableSortedSet.of(FileExt.CSS),
        listOptionsNode,
        CompileOneBundle.FOLLOWER_EXTS);

    return listOptionsNode;
  }
}
