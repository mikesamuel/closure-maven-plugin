package com.google.closure.plugin.plan;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.Serializable;
import java.util.Map;

import com.google.closure.plugin.common.Sources.Source;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import com.google.closure.plugin.plan.BundlingPlanGraphNode.BundleStateVector;

/**
 * A plan graph node that groups inputs into compilation bundles.
 */
public abstract class BundlingPlanGraphNode<
    O extends Serializable,
    B extends BundlingPlanGraphNode.Bundle>
extends SourceSpecedPlanGraphNode<BundleStateVector<O, B>> {
  protected final O options;
  protected Optional<ImmutableList<B>> bundles = Optional.absent();

  protected BundlingPlanGraphNode(PlanContext context, O options) {
    super(context);
    this.options = options;
  }

  /** The options used to build the bundle. */
  public O getOptions() {
    return options;
  }

  @Override
  protected boolean hasChangedInputs() throws IOException {
    if (!bundles.isPresent()) {
      return true;
    }

    return super.hasChangedInputs();
  }

  protected abstract PlanGraphNode<?> fanOutTo(B bundle);

  protected abstract B bundleForFollower(PlanGraphNode<?> f);

  @Override
  protected void markOutputs() {
    // No outputs.
  }

  @Override
  protected
  Optional<ImmutableList<PlanGraphNode<?>>> rebuildFollowersList(JoinNodes jn) {
    try {
      // Hash each followers bundle so we can reuse as appropriate.
      Map<Hash, PlanGraphNode<?>> hashToFollower = Maps.newLinkedHashMap();
      for (PlanGraphNode<?> f : getFollowerList()) {
        Hash h = Hash.hashSerializable(bundleForFollower(f));
        hashToFollower.put(h, f);
      }

      boolean changed = false;
      ImmutableList.Builder<PlanGraphNode<?>> out = ImmutableList.builder();
      for (B bundle : bundles.or(ImmutableList.<B>of())) {
        Hash bHash = Hash.hashSerializable(bundle);
        PlanGraphNode<?> f = hashToFollower.remove(bHash);
        if (f == null) {
          changed = true;
          f = fanOutTo(bundle);
        }
        out.add(f);
      }
      if (!hashToFollower.isEmpty()) {
        changed = true;
      }

      if (changed) {
        return Optional.of(out.build());
      } else {
        return Optional.absent();
      }
    } catch (NotSerializableException ex) {
      throw (AssertionError) new AssertionError().initCause(ex);
    }
  }


  /**
   * A bundle of source files.
   */
  public interface Bundle extends Serializable {
    /** The inputs which can be checked against the build context. */
    ImmutableCollection<Source> getInputs();
  }

  /**
   * State vector for a bundling node.
   */
  public static abstract
  class BundleStateVector<O extends Serializable, B extends Bundle>
  implements PlanGraphNode.StateVector {
    private static final long serialVersionUID = 1L;
    protected final O options;

    protected BundleStateVector(O options) {
      this.options = options;
    }

    /** The bundles. */
    public abstract ImmutableList<B> getBundles();

    @Override
    public abstract
    BundlingPlanGraphNode<O, B> reconstitute(PlanContext context, JoinNodes jn);
  }
}
