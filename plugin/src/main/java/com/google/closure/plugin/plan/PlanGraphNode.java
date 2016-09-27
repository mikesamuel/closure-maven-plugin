package com.google.closure.plugin.plan;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * A plan graph node is a node in the builder graph.
 * <p>
 * The lifecycle of a plan graph node is
 * <ol>
 *   <li>Check inputs to see whether anything has changed.
 *   <li>Process input files.
 *   <li>Update the follower list.
 *   <li>Mark outputs that have changed.
 * </ol>
 *
 * <p>
 * After a compile, the graph can be serialized.
 * Serializing a plan graph involves serializing each node's state-vector and
 * the edges.
 * <p>
 * When its time to do an incremental recompilation, the plan graph can be
 * reconstituted via {@link PlanGraphNode.StateVector#reconstitute}.
 */
public abstract class PlanGraphNode<V extends PlanGraphNode.StateVector> {
  protected final PlanContext context;

  private final List<PlanGraphNode<?>> followers = Lists.newArrayList();

  protected PlanGraphNode(PlanContext context) {
    this.context = context;
  }

  /**
   * True if an input might have changed.
   */
  protected abstract boolean hasChangedInputs() throws IOException;

  protected abstract void processInputs()
  throws IOException, MojoExecutionException;

  /**
   * Make sure that the follower list is up-to-date.
   */
  protected abstract
  Optional<? extends Iterable<? extends PlanGraphNode<?>>>
      rebuildFollowersList(JoinNodes joinNodes)
  throws MojoExecutionException;

  /**
   * Bring the followers list up-to-date with any changes found in input files.
   */
  protected void setFollowerList(
      Iterable<? extends PlanGraphNode<?>> newFollowers) {
    this.followers.clear();
    for (PlanGraphNode<?> rebuiltFollower : newFollowers) {
      // TODO: ideally we'd detect cycles here
      this.followers.add(Preconditions.checkNotNull(rebuiltFollower));
    }
  }

  /** This nodes followers.  May change as inputs change. */
  public ImmutableList<PlanGraphNode<?>> getFollowerList() {
    return ImmutableList.copyOf(followers);
  }

  protected void addFollower(PlanGraphNode<?> n) {
    this.followers.add(Preconditions.checkNotNull(n));
  }

  /**
   * Alert the build context of any outputs that have changed.
   */
  protected abstract void markOutputs();

  protected abstract V getStateVector();


  @Override
  public String toString() {
    return getClass().getSimpleName();
  }


  /**
   * Serializable state for a plan graph node.
   */
  public interface StateVector extends Serializable {

    /** Reconstitute a plan graph node from its deserialized state vector. */
    PlanGraphNode<?> reconstitute(PlanContext c, JoinNodes joinNodes);
  }
}
