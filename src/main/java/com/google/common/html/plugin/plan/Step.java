package com.google.common.html.plugin.plan;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * A step in the process of transforming inputs to outputs.
 */
public abstract class Step {
  protected final String key;
  protected final ImmutableList<Ingredient> inputs;
  protected final ImmutableSet<StepSource> reads;
  protected final ImmutableSet<StepSource> writes;

  /**
   * @param key a key used to associate a step with a hash of its inputs in
   *    a {@link HashStore}.
   * @param reads used, in conjunction with writes, to establish a partial
   *    order of steps within a {@link Plan} by ensuring that writes occur
   *    before reads.
   */
  protected Step(
      String key,
      ImmutableList<Ingredient> inputs,
      ImmutableSet<StepSource> reads,
      ImmutableSet<StepSource> writes) {
    this.key = key;
    this.inputs = inputs;
    this.reads = reads;
    this.writes = writes;
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