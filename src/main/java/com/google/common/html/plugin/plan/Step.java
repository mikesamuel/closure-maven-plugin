package com.google.common.html.plugin.plan;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.collect.ImmutableList;

/**
 * A step in the process of transforming inputs to outputs.
 */
public abstract class Step {
  protected final String key;
  protected final ImmutableList<Ingredient> inputs;
  protected final ImmutableList<Ingredient> outputs;

  protected Step(
      String key,
      ImmutableList<Ingredient> inputs,
      ImmutableList<Ingredient> outputs) {
    this.key = key;
    this.inputs = inputs;
    this.outputs = outputs;
  }

  /**
   * Transforms the inputs to outputs which can be persisted in the file-system
   * and hashed.
   */
  public abstract void execute(Log log) throws MojoExecutionException;

  /**
   * Called when execution can be skipped because input hashes are the same.
   */
  public abstract void skip(Log log) throws MojoExecutionException;

  /**
   * Any extra steps that are required as part of this steps completion.
   * If the plan cannot be statically known, then a step may introduce more
   * steps this way.
   */
  public abstract ImmutableList<Step> extraSteps(Log log)
  throws MojoExecutionException;
}