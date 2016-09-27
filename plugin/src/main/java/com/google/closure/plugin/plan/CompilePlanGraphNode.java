package com.google.closure.plugin.plan;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import com.google.closure.plugin.common.Sources.Source;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * A plan node that compiles one bundle.
 */
public abstract class CompilePlanGraphNode<
    O extends Serializable & StructurallyComparable,
    B extends BundlingPlanGraphNode.Bundle>
extends PlanGraphNode<CompilePlanGraphNode.CompileStateVector<O, B>> {

  /** Options for compiler. */
  public final O options;
  /** The bundle to compile. */
  public final B bundle;

  protected Optional<ImmutableList<File>> outputs = Optional.absent();

  protected CompilePlanGraphNode(PlanContext context, O options, B bundle) {
    super(context);
    this.options = options;
    this.bundle = bundle;
  }


  @Override
  protected boolean hasChangedInputs() throws IOException {
    if (!context.buildContext.isIncremental()) {
      return true;
    }
    if (!outputs.isPresent()) {
      return false;
    }
    for (File f : outputs.get()) {
      if (!f.exists() || context.buildContext.hasDelta(f)) {
        return true;
      }
    }
    for (Source s : bundle.getInputs()) {
      if (context.buildContext.hasDelta(s.canonicalPath)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void markOutputs() {
    for (File f : outputs.get()) {
      context.buildContext.refresh(f);
    }
  }

  /** State vector for a compiler. */
  public static abstract class CompileStateVector<
      O extends Serializable,
      B extends BundlingPlanGraphNode.Bundle>
  implements PlanGraphNode.StateVector {
    private static final long serialVersionUID = 1;
    /** Options for the compiler. */
    public final O options;
    /** The bundle to compile. */
    public final B bundle;
    /** Compiler outputs. */
    public final Optional<ImmutableList<File>> outputs;

    protected CompileStateVector(
        O options,
        B bundle,
        Optional<ImmutableList<File>> outputs) {
      this.options = options;
      this.bundle = bundle;
      this.outputs = outputs;
    }
  }
}
