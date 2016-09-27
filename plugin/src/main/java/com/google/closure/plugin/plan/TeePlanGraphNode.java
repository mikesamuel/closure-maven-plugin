package com.google.closure.plugin.plan;

import java.io.IOException;
import java.util.Arrays;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Optional;

/**
 * A plan graph node that does no works but which may have multiple followers.
 * This is useful when a node needs to fan out 1:1 some internal data structure
 * to followers and be able to reverse that mapping.
 */
public class TeePlanGraphNode extends PlanGraphNode<TeePlanGraphNode.SV> {

  /** */
  public TeePlanGraphNode(
      PlanContext context,
      PlanGraphNode<?>... followers) {
    this(context, Arrays.asList(followers));
  }

  /** */
  public TeePlanGraphNode(
      PlanContext context,
      Iterable<? extends PlanGraphNode<?>> followers) {
    super(context);
    this.setFollowerList(followers);
  }

  static final class SV implements PlanGraphNode.StateVector {

    private static final long serialVersionUID = -2180358632428004843L;

    @Override
    public
    PlanGraphNode<?> reconstitute(PlanContext planContext, JoinNodes jn) {
      return new TeePlanGraphNode(planContext);
    }
  }

  @Override
  protected boolean hasChangedInputs() throws IOException {
    return false;
  }

  @Override
  protected void processInputs() throws IOException, MojoExecutionException {
    // Done
  }

  @Override
  protected
  Optional<Iterable<PlanGraphNode<?>>> rebuildFollowersList(JoinNodes jn) {
    return Optional.absent();
  }

  @Override
  protected void markOutputs() {
    // Done
  }

  @Override
  protected SV getStateVector() {
    return new SV();
  }

  @Override
  public String toString() {
    return "{" + getClass().getSimpleName() + " " + getFollowerList() + "}";
  }
}
