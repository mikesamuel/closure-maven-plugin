package com.google.closure.plugin.plan;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.closure.plugin.common.Identifiable;
import com.google.closure.plugin.common.StructurallyComparable;
import com.google.closure.plugin.plan.OptionPlanGraphNode.OptionsAndInputs;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * A bundling plan graph node that computes bundles from a previous
 * bundling node's bundles instead of from the options and inputs from an
 * options node.
 */
public abstract class RebundlingPlanGraphNode<
  O extends Serializable & StructurallyComparable & Identifiable,
  B extends BundlingPlanGraphNode.Bundle,
  C extends BundlingPlanGraphNode.Bundle>
extends BundlingPlanGraphNode<O, C> {

  protected Optional<Update<OptionsAndBundles<O, B>>> inputBundles =
      Optional.absent();

  private Map<OptionsAndInputs<O>, OptionsAndBundles<O, B>> memoTable;

  protected RebundlingPlanGraphNode(PlanContext context) {
    super(context);
  }

  @Override
  protected final ImmutableList<C> bundlesFor(
      Optional<ImmutableList<C>> oldBundles, OptionsAndInputs<O> oi)
  throws IOException, MojoExecutionException {
    Map<OptionsAndInputs<O>, OptionsAndBundles<O, B>> inputsToBundles =
        getBundleMap();
    return bundlesFor(
        oldBundles, Preconditions.checkNotNull(inputsToBundles.get(oi)));
  }

  protected abstract ImmutableList<C> bundlesFor(
      Optional<ImmutableList<C>> oldBundles,
      OptionsAndBundles<O, B> ob)
  throws IOException, MojoExecutionException;


  @Override
  protected void preExecute(Iterable<? extends PlanGraphNode<?>> preceders) {
    this.inputBundles = Optional.absent();
    this.optionsUpdate = Optional.absent();
    for (PlanGraphNode<?> p : preceders) {
      if (p instanceof BundlingPlanGraphNode<?, ?>) {
        Preconditions.checkState(!inputBundles.isPresent());
        Preconditions.checkState(!optionsUpdate.isPresent());

        @SuppressWarnings("unchecked")  // TODO: unsound
        BundlingPlanGraphNode<O, B> bn = (BundlingPlanGraphNode<O, B>) p;
        this.optionsUpdate = bn.optionsUpdate;
        this.inputBundles = bn.optionsAndBundles;
      }
    }
    Preconditions.checkState(inputBundles.isPresent());
    Preconditions.checkState(optionsUpdate.isPresent());
  }

  private
  Map<OptionsAndInputs<O>, OptionsAndBundles<O, B>> getBundleMap() {
    if (memoTable == null) {
      ImmutableMap.Builder<
          OptionsAndInputs<O>, OptionsAndBundles<O, B>> b =
          ImmutableMap.builder();
      Update<OptionsAndBundles<O, B>> u = this.inputBundles.get();
      for (OptionsAndBundles<O, B> ob : u.all()) {
        b.put(ob.optionsAndInputs, ob);
      }
      memoTable = b.build();
    }
    return memoTable;
  }

  @Override
  protected abstract RebundleStateVector<O, B, C> getStateVector();

  /**
   * A state vector for a rebundling node.
   */
  public static abstract class RebundleStateVector<
      O extends Serializable & StructurallyComparable & Identifiable,
      B extends BundlingPlanGraphNode.Bundle,
      C extends BundlingPlanGraphNode.Bundle>
  extends BundleStateVector<O, C> {

    private static final long serialVersionUID = 1L;

    private Optional<Update<OptionsAndBundles<O, B>>> inputBundles =
        Optional.absent();

    protected RebundleStateVector(RebundlingPlanGraphNode<O, B, C> node) {
      super(node);
      this.inputBundles = node.inputBundles;
    }

    @Override
    protected
    <N extends BundlingPlanGraphNode<O, C>>
    N apply(N node) {
      @SuppressWarnings("unchecked")  // TODO: unsound
      RebundlingPlanGraphNode<O, B, C> rbNode =
          (RebundlingPlanGraphNode<O, B, C>) super.apply(node);
      rbNode.inputBundles = inputBundles;
      return node;
    }
  }
}
