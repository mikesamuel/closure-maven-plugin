package com.google.closure.plugin.plan;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import com.google.closure.plugin.common.StructurallyComparable;
import com.google.closure.plugin.common.Identifiable;
import com.google.closure.plugin.plan.BundlingPlanGraphNode.OptionsAndBundles;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * A plan node that receives bundles from a {@link BundlingPlanGraphNode} and
 * compiles them to produce outputs.
 * <p>
 * The choice of whether to compile one at a time or all at once is left up to
 * the subclass.
 */
public abstract class CompilePlanGraphNode<
    O extends Serializable & StructurallyComparable & Identifiable,
    B extends BundlingPlanGraphNode.Bundle>
extends PlanGraphNode<CompilePlanGraphNode.CompileStateVector<O, B>> {

  /** Options for compiler. */
  public Optional<Update<OptionsAndBundles<O, B>>> optionsAndBundles;
  /** The bundle to compile. */
  public final List<File> outputFiles = Lists.newArrayList();

  protected CompilePlanGraphNode(PlanContext context) {
    super(context);
  }

  /** Called with preceders to allow. */
  @Override
  protected void preExecute(Iterable<? extends PlanGraphNode<?>> preceders) {
    this.optionsAndBundles = Optional.absent();
    for (PlanGraphNode<?> p : preceders) {
      if (p instanceof BundlingPlanGraphNode<?, ?>) {
        @SuppressWarnings("unchecked")  // TODO: unsound
        BundlingPlanGraphNode<O, B> bundler = (BundlingPlanGraphNode<O, B>) p;
        Preconditions.checkState(!optionsAndBundles.isPresent());
        this.optionsAndBundles = bundler.getOptionsAndBundles();
      }
    }
    Preconditions.checkState(optionsAndBundles.isPresent());
  }

  /**
   * By default, nothing to do since the bundler has done the work of
   * triaging bundles.
   */
  @Override
  protected void filterUpdates() throws IOException {
    // Done
  }

  /**
   * After processing, the list of files that were changed.
   */
  @Override
  protected Iterable<? extends File> changedOutputFiles() {
    return ImmutableList.copyOf(outputFiles);
  }

  /** Deletes the file and notifies the build context of that. */
  protected void deleteIfExists(File f) throws IOException {
    if (f != null && f.exists()) {
      if (!f.delete()) {
        throw new IOException("Failed to delete " + f);
      }
      this.outputFiles.add(f);
    }
  }


  /** State vector for a compiler. */
  public static abstract class CompileStateVector<
      O extends Serializable & StructurallyComparable & Identifiable,
      B extends BundlingPlanGraphNode.Bundle>
  implements PlanGraphNode.StateVector {
    private static final long serialVersionUID = 1;

    final Optional<Update<OptionsAndBundles<O, B>>> optionsAndBundles;
    final ImmutableList<File> outputFiles;

    protected CompileStateVector(CompilePlanGraphNode<O, B> node) {
      this.optionsAndBundles = node.optionsAndBundles;
      this.outputFiles = ImmutableList.copyOf(node.outputFiles);
    }

    protected <N extends CompilePlanGraphNode<O, B>>
    N apply(N node) {
      node.outputFiles.clear();
      node.outputFiles.addAll(outputFiles);
      node.optionsAndBundles = optionsAndBundles;
      return node;
    }
  }
}
