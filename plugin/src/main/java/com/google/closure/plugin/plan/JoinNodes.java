package com.google.closure.plugin.plan;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.closure.plugin.common.FileExt;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;

/**
 * Pub/sub system based on input and output
 * {@linkplain FileExt source file extensions} to link sub-graphs together
 * without tight coupling.
 * <p>
 * A producer of {@code *.foo} that knows of nothing that should follow it
 * may depend on the {@code AfterFoo} join node.
 * <p>
 * Consumers of {@code *.foo} generated files can register themselves as
 * followers of {@code AfterFoo}.
 */
public final class JoinNodes {
  private final PlanContext context;

  /** */
  public JoinNodes(PlanContext context) {
    this.context = context;
  }

  private final Multimap<FileExt, JoinPlanGraphNode> joinNodesByExtension =
      Multimaps.newListMultimap(
          Maps.<FileExt, Collection<JoinPlanGraphNode>>newLinkedHashMap(),
          new Supplier<List<JoinPlanGraphNode>>() {
            @Override
            public List<JoinPlanGraphNode> get() {
              return Lists.newArrayList();
            }
          });

  private final
  Map<ImmutableSortedSet<FileExt>, JoinPlanGraphNode> joinNodesByExtensionSet =
      Maps.newLinkedHashMap();

  private final List<PipelineConstraint> pipelineConstraints =
      Lists.newArrayList();

  /**
   * The followers of producers of the given file extensions.
   */
  public ImmutableList<PlanGraphNode<?>> followersOf(FileExt... extensions) {
    return followersOf(Arrays.asList(extensions));
  }

  /**
   * The followers of producers of the given file extensions.
   */
  public ImmutableList<PlanGraphNode<?>> followersOf(
      Iterable<? extends FileExt> extensions) {

    ImmutableSet<FileExt> extensionSet =
        ImmutableSortedSet.<FileExt>naturalOrder()
        .addAll(extensions)
        // Add an implicit * so that one can follow "*" to follow producers
        // of any output.
        .add(FileExt._ANY)
        .build();

    Set<PlanGraphNode<?>> joinNodesSeen = Sets.newIdentityHashSet();
    ImmutableList.Builder<PlanGraphNode<?>> out = ImmutableList.builder();
    for (FileExt extension : extensionSet) {
      for (JoinPlanGraphNode n : joinNodesByExtension.get(extension)) {
        if (joinNodesSeen.add(n)) {
          out.add(n);
        }
      }
    }
    return out.build();
  }

  /**
   * Called to indicate that the given node follows producers of the
   * given extensions.
   */
  public void follows(PlanGraphNode<?> follower, FileExt... extensions) {
    ImmutableSortedSet<FileExt> extensionSet =
        ImmutableSortedSet.<FileExt>naturalOrder()
        .add(extensions)
        .build();
    JoinPlanGraphNode j = joinNodeThatJoins(extensionSet);
    j.addFollower(follower);
  }

  JoinPlanGraphNode joinNodeThatJoins(
      ImmutableSortedSet<FileExt> extensionSet) {
    JoinPlanGraphNode j = this.joinNodesByExtensionSet.get(extensionSet);
    if (j == null) {
      // Create a node.
      j = new JoinPlanGraphNode(context, extensionSet);
      // Make sure that the node for ("foo", "bar")
      // is preceded by ("foo") and ("bar")
      // but special case ("*") so that it follows everything and precedes
      // nothing.
      this.joinNodesByExtensionSet.put(extensionSet, j);
      for (FileExt ext : j.extensions) {
        this.joinNodesByExtension.put(ext, j);
      }
    }
    return j;
  }

  /**
   * All the join nodes for extensions currently registered.
   */
  public Iterable<? extends PlanGraphNode<?>> allJoinNodes() {
    return Collections.unmodifiableCollection(
        this.joinNodesByExtensionSet.values());
  }

  /**
   * Declares that the pipeline requires all inputs of prerequisite kinds,
   * starts with the given node, and produces sources of postrequisite kinds.
   */
  public void pipeline(
      Iterable<? extends FileExt> prerequisites,
      PlanGraphNode<?> node,
      Iterable<? extends FileExt> postrequisites) {
    // Instead of adding followers now, we wait until all the pipeline
    // constraints have been declared.
    pipelineConstraints.add(
        new PipelineConstraint(prerequisites, node, postrequisites));
  }

  /**
   * Must be called to realize lazy pipeline constraints after all the
   * plan graph is created but before it is executed.
   */
  public void realizePipelineConstraints() {
    for (PipelineConstraint pc : this.pipelineConstraints) {
      pc.realize();
    }
    this.pipelineConstraints.clear();
  }


  /**
   * A node that joins loosely coupled (in the software dependency sense)
   * sub-graphs.
   */
  public static final class JoinPlanGraphNode
  extends PlanGraphNode<JoinPlanGraphNode.JoinStateVector> {
    final ImmutableSortedSet<FileExt> extensions;

    private static final class JoinStateVector
    implements PlanGraphNode.StateVector {

      private static final long serialVersionUID = -1911896838093511492L;

      final ImmutableSortedSet<FileExt> extensions;

      JoinStateVector(ImmutableSortedSet<FileExt> extensions) {
        this.extensions = extensions;
      }

      @Override
      public PlanGraphNode<?> reconstitute(PlanContext c, JoinNodes joinNodes) {
        return joinNodes.joinNodeThatJoins(extensions);
      }
    }

    JoinPlanGraphNode(
        PlanContext context, ImmutableSortedSet<FileExt> extensions) {
      super(context);
      Preconditions.checkArgument(!extensions.isEmpty());
      this.extensions = extensions;
    }

    @Override
    protected boolean hasChangedInputs() {
      return false;
    }

    @Override
    protected void processInputs() throws IOException, MojoExecutionException {
      throw new AssertionError("Not dirty");
    }

    @Override
    protected
    Optional<ImmutableList<PlanGraphNode<?>>>
        rebuildFollowersList(JoinNodes jn) {
      return Optional.absent();
    }

    @Override
    protected void markOutputs() {
      // Done
    }

    @Override
    protected JoinStateVector getStateVector() {
      return new JoinStateVector(extensions);
    }

    @Override
    public String toString() {
      return "{JoinPlanGraphNode " + extensions + "}";
    }
  }

  final class PipelineConstraint {
    final Optional<JoinPlanGraphNode> preReqNode;
    final PlanGraphNode<?> node;
    final Optional<JoinPlanGraphNode> postReqNode;

    PipelineConstraint(
        Iterable<? extends FileExt> prerequisites,
        PlanGraphNode<?> node,
        Iterable<? extends FileExt> postrequisites) {
      this.preReqNode = Iterables.isEmpty(prerequisites)
          ? Optional.<JoinPlanGraphNode>absent()
          : Optional.of(
              joinNodeThatJoins(ImmutableSortedSet.copyOf(prerequisites)));
      this.node = node;
      // We need to make the joiner node even when prerequisites is empty so
      // that pipelines can declare all the multi-extension sets used and
      // to allow followersOf to establish proper sub-set/super-set
      // relationships without exploding the node count.
      this.postReqNode = Iterables.isEmpty(postrequisites)
          ? Optional.<JoinPlanGraphNode>absent()
          : Optional.of(
              joinNodeThatJoins(ImmutableSortedSet.copyOf(postrequisites)));
    }

    void realize() {
      if (preReqNode.isPresent()) {
        if (postReqNode.isPresent()) {
          for (PlanGraphNode<?> postreq
               : followersOf(postReqNode.get().extensions)) {
            preReqNode.get().addFollower(postreq);
          }
        }
        preReqNode.get().addFollower(node);
      }
    }
  }
}
