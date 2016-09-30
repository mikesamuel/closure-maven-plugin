package com.google.closure.plugin.plan;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.sonatype.plexus.build.incremental.BuildContext;

import com.google.closure.plugin.common.StructurallyComparable;
import com.google.closure.plugin.common.Identifiable;
import com.google.closure.plugin.common.Sources.Source;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import com.google.closure.plugin.plan.BundlingPlanGraphNode.BundleStateVector;
import com.google.closure.plugin.plan.OptionPlanGraphNode.OptionsAndInputs;

/**
 * A plan graph node that processes options and inputs' metadata to produce
 * bundles of inputs ready for compilation by a {@link CompilePlanGraphNode}.
 */
public abstract class BundlingPlanGraphNode<
    O extends Serializable & StructurallyComparable & Identifiable,
    B extends BundlingPlanGraphNode.Bundle>
extends PlanGraphNode<BundleStateVector<O, B>> {
  /** Input from predecessor. */
  protected
  Optional<Update<OptionsAndInputs<O>>> optionsUpdate = Optional.absent();
  /** The output for the compile stage. */
  protected Optional<Update<OptionsAndBundles<O, B>>> optionsAndBundles =
      Optional.absent();

  protected BundlingPlanGraphNode(PlanContext context) {
    super(context);
  }

  /**
   * The default implementation looks for option nodes and aggregates
   * options from them.
   */
  @Override
  protected void preExecute(Iterable<? extends PlanGraphNode<?>> preceders) {
    this.optionsUpdate = Optional.absent();
    for (PlanGraphNode<?> p : preceders) {
      if (p instanceof OptionPlanGraphNode<?>) {
        @SuppressWarnings("unchecked")  // Optimistic but unsound.  TODO
        OptionPlanGraphNode<O> optionsNode = (OptionPlanGraphNode<O>) p;
        this.optionsUpdate = optionsNode.getUpdates();
      }
    }
  }

  /**
   * Derive bundles from source lists.
   */
  @Override
  protected void filterUpdates() throws IOException, MojoExecutionException {
    Preconditions.checkState(optionsUpdate.isPresent());

    BuildContext buildContext = context.buildContext;
    boolean isIncremental = buildContext.isIncremental();

    Map<OptionsAndInputs<O>, OptionsAndBundles<O, B>> previous =
        Maps.newLinkedHashMap();
    if (isIncremental && optionsAndBundles.isPresent()) {
      for (OptionsAndBundles<O, B> ob : optionsAndBundles.get().allExtant()) {
        previous.put(ob.optionsAndInputs, ob);
      }
    }

    ImmutableList.Builder<OptionsAndBundles<O, B>> changed =
        ImmutableList.builder();
    ImmutableList.Builder<OptionsAndBundles<O, B>> unchanged =
        ImmutableList.builder();
    ImmutableList.Builder<OptionsAndBundles<O, B>> defunct =
        ImmutableList.builder();

    Update<OptionsAndInputs<O>> u = this.optionsUpdate.get();
    for (OptionsAndInputs<O> oi : u.changed) {
      OptionsAndBundles<O, B> old = previous.remove(oi);
      Optional<ImmutableList<B>> oldBundles =
          old != null
          ? Optional.of(old.bundles)
          : Optional.<ImmutableList<B>>absent();
      changed.add(new OptionsAndBundles<>(oi, bundlesFor(oldBundles, oi)));
    }

    for (OptionsAndInputs<O> oi : u.unchanged) {
      OptionsAndBundles<O, B> ob = previous.remove(oi);
      if (ob == null) {
        ob = new OptionsAndBundles<>(
            oi,
            bundlesFor(Optional.<ImmutableList<B>>absent(), oi));
      }
      unchanged.add(ob);
    }

    defunct.addAll(previous.values());

    this.optionsAndBundles = Optional.of(new Update<>(
        unchanged.build(),
        changed.build(),
        defunct.build()
        ));
  }

  /**
   * Constructs bundles from the given inputs.
   * @param oldBundles previously computed.
   */
  protected abstract ImmutableList<B> bundlesFor(
      Optional<ImmutableList<B>> oldBundles, OptionsAndInputs<O> oi)
  throws IOException, MojoExecutionException;

  /** This default implementaiton does nothing. */
  @Override
  protected void process() throws IOException, MojoExecutionException {
    // nop
  }

  /**
   * By default, this changes no files.
   */
  @Override
  protected Iterable<? extends File> changedOutputFiles() {
    return ImmutableList.of();
  }

  /**
   * The output bundles associated with the options from which they were
   * derived.
   */
  public Optional<Update<OptionsAndBundles<O, B>>> getOptionsAndBundles() {
    return this.optionsAndBundles;
  }

  /**
   * The options and inputs from this node's predecessor.
   */
  public Optional<Update<OptionsAndInputs<O>>> optionsAndInputs() {
    return this.optionsUpdate;
  }

  /**
   * A bundle of source files.
   */
  public interface Bundle extends Serializable, StructurallyComparable {
    /** The inputs which can be checked against the build context. */
    ImmutableCollection<Source> getInputs();
  }

  /**
   * State vector for a bundling node.
   */
  public static abstract
  class BundleStateVector<
      O extends Serializable & StructurallyComparable & Identifiable,
      B extends Bundle>
  implements PlanGraphNode.StateVector {
    private static final long serialVersionUID = 1L;

    protected final Optional<Update<OptionsAndInputs<O>>> optionsUpdate;

    protected final Optional<Update<OptionsAndBundles<O, B>>> optionsAndBundles;

    protected BundleStateVector(BundlingPlanGraphNode<O, B> node) {
      this.optionsUpdate = node.optionsUpdate;
      this.optionsAndBundles = node.optionsAndBundles;
    }

    protected
    <N extends BundlingPlanGraphNode<O, B>>
    N apply(N node) {
      node.optionsUpdate = optionsUpdate;
      node.optionsAndBundles = optionsAndBundles;
      return node;
    }
  }

  /**
   * Options and bundles derived from it.
   */
  public static final class OptionsAndBundles<
      O extends Serializable & StructurallyComparable & Identifiable,
      B extends Bundle>
  implements Serializable, StructurallyComparable {
    private static final long serialVersionUID = 1L;

    /** The options associated with bundles. */
    public final OptionsAndInputs<O> optionsAndInputs;
    /** Bundles derived from the sources specified by options. */
    public final ImmutableList<B> bundles;

    /** */
    public OptionsAndBundles(
        OptionsAndInputs<O> optionsAndInputs, Iterable<? extends B> bundles) {
      this.optionsAndInputs = optionsAndInputs;
      this.bundles = ImmutableList.copyOf(bundles);
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((bundles == null) ? 0 : bundles.hashCode());
      result = prime * result
          + ((optionsAndInputs == null) ? 0 : optionsAndInputs.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      OptionsAndBundles<?, ?> other = (OptionsAndBundles<?, ?>) obj;
      if (bundles == null) {
        if (other.bundles != null) {
          return false;
        }
      } else if (!bundles.equals(other.bundles)) {
        return false;
      }
      if (optionsAndInputs == null) {
        if (other.optionsAndInputs != null) {
          return false;
        }
      } else if (!optionsAndInputs.equals(other.optionsAndInputs)) {
        return false;
      }
      return true;
    }
  }
}
