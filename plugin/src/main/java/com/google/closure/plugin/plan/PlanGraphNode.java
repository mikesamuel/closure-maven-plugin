package com.google.closure.plugin.plan;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * A plan graph node is a node in the builder graph.
 * <p>
 * The lifecycle of a node is
 * <ol>
 *   <li>construct or reconstitute from state vector
 *   <li>link to followers
 *   <li>receive inputs from preceders and/or check the build context for
 *       file-system updates
 *   <li>process inputs that need processing
 *   <li>advertise which output files changed so that the plan graph can
 *       tell the build context which files changed.
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
   * Called with preceders to allow fetching intermediate inputs from earlier
   * stages.
   */
  protected abstract void preExecute(
      Iterable<? extends PlanGraphNode<?>> preceders);

  /**
   * Filter inputs into those that need recompile and those that do not.
   */
  protected abstract void filterUpdates()
  throws IOException, MojoExecutionException;

  /**
   * Process inputs to produce outputs.
   */
  protected abstract void process()
  throws IOException, MojoExecutionException;

  /**
   * After processing, the list of files that were changed.
   */
  protected abstract Iterable<? extends File> changedOutputFiles();

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
