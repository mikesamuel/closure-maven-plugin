package com.google.closure.plugin.plan;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Option nodes follow no nodes and simply fan-out their individual option sets
 * to other nodes, typically ones that gather inputs and find bundles.
 *
 * @param <O> bundle of options
 */
public abstract class OptionPlanGraphNode<O extends Serializable>
extends PlanGraphNode<OptionPlanGraphNode.OptionStateVector<O>> {
  private final List<O> optionSets = Lists.newArrayList();

  protected OptionPlanGraphNode(PlanContext context) {
    super(context);
  }

  protected void addOptionSet(O opts) {
    optionSets.add(Preconditions.checkNotNull(opts));
  }

  /** Replaces the contained option sets. */
  public void setOptionSets(Iterable<? extends O> newOptionSets) {
    optionSets.clear();
    for (O newOptionSet : newOptionSets) {
      optionSets.add(newOptionSet);
    }
  }

  /** The option sets available. */
  public List<O> getOptionSets() {
    return Collections.unmodifiableList(optionSets);
  }

  /**
   * A state vector that contains one or more sets of options.
   */
  public static abstract class OptionStateVector<O extends Serializable>
  implements PlanGraphNode.StateVector {

    private static final long serialVersionUID = -5046502010496876086L;

    /** The option sets that are fanned-out to followers. */
    public final ImmutableList<O> optionSets;

    protected OptionStateVector(ImmutableList<O> optionSets) {
      this.optionSets = optionSets;
    }
  }

  @Override
  protected boolean hasChangedInputs() throws IOException {
    return false;
  }

  @Override
  protected void processInputs() throws IOException, MojoExecutionException {
    // Done.
  }


  @Override
  protected void markOutputs() {
    // No direct outputs
  }

  @Override
  protected final
  Optional<ImmutableList<PlanGraphNode<?>>> rebuildFollowersList(JoinNodes jn)
  throws MojoExecutionException {

    try {
      Map<Hash, PlanGraphNode<?>> hashToFollower = Maps.newHashMap();
      for (PlanGraphNode<?> f : getFollowerList()) {
        Hash fHash = Hash.hashSerializable(this.getOptionsForFollower(f));
        hashToFollower.put(fHash, f);
      }

      boolean changed = false;
      ImmutableList.Builder<PlanGraphNode<?>> out = ImmutableList.builder();
      for (O opts : this.optionSets) {
        Hash oHash = Hash.hashSerializable(opts);
        PlanGraphNode<?> f = hashToFollower.remove(oHash);
        if (f == null) {
          changed = true;
          f = fanOutTo(opts);
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

  protected abstract PlanGraphNode<?> fanOutTo(O options)
  throws MojoExecutionException;

  protected abstract O getOptionsForFollower(PlanGraphNode<?> follower);
}
