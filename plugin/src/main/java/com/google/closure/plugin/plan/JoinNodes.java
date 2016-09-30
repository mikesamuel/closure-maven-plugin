package com.google.closure.plugin.plan;

import java.io.File;
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
   * A fresh pipeline builder that allows chaining together a sequence of
   * nodes and the kinds of files that the pipeline needs and the kinds
   * it produces which allows the plan graph to slot it into the right place.
   */
  public PipelineBuilder pipeline() {
    return new PipelineBuilder();
  }

  /**
   * Must be called to realize lazy pipeline constraints after all the
   * plan graph is created but before it is executed.
   *
   * @return Any added roots.
   */
  public ImmutableList<PlanGraphNode<?>> realizePipelineConstraints() {
    ImmutableList.Builder<PlanGraphNode<?>> newRoots = ImmutableList.builder();
    for (PipelineConstraint pc : this.pipelineConstraints) {
      Optional<ImmutableList<PlanGraphNode<?>>> newRoot = pc.realize();
      if (newRoot.isPresent()) {
        newRoots.addAll(newRoot.get());
      }
    }
    this.pipelineConstraints.clear();
    return newRoots.build();
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
    protected JoinStateVector getStateVector() {
      return new JoinStateVector(extensions);
    }

    @Override
    public String toString() {
      return "{JoinPlanGraphNode " + extensions + "}";
    }

    @Override
    protected void preExecute(Iterable<? extends PlanGraphNode<?>> preceders) {
      // Nop
    }

    @Override
    protected void filterUpdates() throws IOException, MojoExecutionException {
      // Nop
    }

    @Override
    protected void process() throws IOException, MojoExecutionException {
      // Nop
    }

    @Override
    protected Iterable<? extends File> changedOutputFiles() {
      return ImmutableList.of();
    }
  }

  final class PipelineConstraint {
    final Optional<JoinPlanGraphNode> preReqNode;
    final ImmutableList<ImmutableList<PlanGraphNode<?>>> layers;
    final Optional<JoinPlanGraphNode> postReqNode;

    PipelineConstraint(
        Iterable<? extends FileExt> prerequisites,
        Iterable<? extends ImmutableList<PlanGraphNode<?>>> nodes,
        Iterable<? extends FileExt> postrequisites) {
      this.preReqNode = Iterables.isEmpty(prerequisites)
          ? Optional.<JoinPlanGraphNode>absent()
          : Optional.of(
              joinNodeThatJoins(ImmutableSortedSet.copyOf(prerequisites)));
      this.layers = ImmutableList.copyOf(nodes);
      Preconditions.checkArgument(!this.layers.isEmpty());
      // We need to make the joiner node even when prerequisites is empty so
      // that pipelines can declare all the multi-extension sets used and
      // to allow followersOf to establish proper sub-set/super-set
      // relationships without exploding the node count.
      this.postReqNode = Iterables.isEmpty(postrequisites)
          ? Optional.<JoinPlanGraphNode>absent()
          : Optional.of(
              joinNodeThatJoins(ImmutableSortedSet.copyOf(postrequisites)));
    }

    Optional<ImmutableList<PlanGraphNode<?>>> realize() {
      Optional<ImmutableList<PlanGraphNode<?>>> newRoots = Optional.absent();
      ImmutableList<PlanGraphNode<?>> previous = ImmutableList.of();
      if (preReqNode.isPresent()) {
        previous = ImmutableList.<PlanGraphNode<?>>of(preReqNode.get());
      } else if (!layers.isEmpty()) {
        // Assumes no empty layers at front.
        newRoots = Optional.of(layers.get(0));
      }

      for (ImmutableList<PlanGraphNode<?>> layer : layers) {
        for (PlanGraphNode<?> p : previous) {
          for (PlanGraphNode<?> node : layer) {
            p.addFollower(node);
          }
        }
        previous = layer;
      }

      if (postReqNode.isPresent()) {
        for (PlanGraphNode<?> p : previous) {
          for (PlanGraphNode<?> postreq
              : followersOf(postReqNode.get().extensions)) {
            p.addFollower(postreq);
          }
        }
      }
      return newRoots;
    }
  }


  /**
   * Allows chaining together a sequence of
   * nodes and the kinds of files that the pipeline needs and the kinds
   * it produces which allows the plan graph to slot it into the right place.
   */
  public final class PipelineBuilder {
    private final ImmutableSortedSet.Builder<FileExt> requires =
        ImmutableSortedSet.naturalOrder();
    private final ImmutableList.Builder<ImmutableList<PlanGraphNode<?>>>
        pipeline = ImmutableList.builder();
    private final ImmutableSortedSet.Builder<FileExt> provides =
        ImmutableSortedSet.naturalOrder();

    /**
     * Specify that the first stage in the pipeline need to have all source
     * files with the given extensions available.
     */
    public PipelineBuilder require(FileExt... exts) {
      return require(Arrays.asList(exts));
    }

    /**
     * Specify that the first stage in the pipeline need to have all source
     * files with the given extensions available.
     */
    public PipelineBuilder require(Iterable<? extends FileExt> exts) {
      requires.addAll(exts);
      return this;
    }

    /**
     * Specify that the pipeline may produce generated sources with the given
     * extensions.  Terminal stages in the pipeline have to happen before
     * any that require any of the given extensions
     */
    public PipelineBuilder provide(FileExt... exts) {
      return provide(Arrays.asList(exts));
    }

    /**
     * Specify that the pipeline may produce generated sources with the given
     * extensions.  Terminal stages in the pipeline have to happen before
     * any that require any of the given extensions
     */
    public PipelineBuilder provide(Iterable<? extends FileExt> exts) {
      provides.addAll(exts);
      return this;
    }

    /**
     * Specify that the given node happens after any previous calls to then
     * in a way that allows {@link PlanGraphNode#preExecute} inspection.
     * If this is the first call to this method for this builder, then node is
     * the root of the pipeline.
     */
    public PipelineBuilder then(PlanGraphNode<?>... nodes) {
      pipeline.add(ImmutableList.copyOf(nodes));
      return this;
    }

    /**
     * Adds a completed pipline constraint which will be added to the graph
     * when it calls {@link JoinNodes#realizePipelineConstraints()}.
     */
    @SuppressWarnings("synthetic-access")
    public void build() {
      JoinNodes.this.pipelineConstraints.add(new PipelineConstraint(
          requires.build(),
          pipeline.build(),
          provides.build()));
    }
  }
}
