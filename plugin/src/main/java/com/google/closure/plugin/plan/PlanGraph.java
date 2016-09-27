package com.google.closure.plugin.plan;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * A graph of plan nodes that is lazily built and which may be stored for
 * incremental recompilation.
 */
public final class PlanGraph {

  final JoinNodes joinNodes;
  final PlanContext context;
  final Set<PlanGraphNode<?>> roots = Sets.newIdentityHashSet();

  /**
   * @param context must have a blank join nodes.
   */
  public PlanGraph(PlanContext context) {
    this.context = context;
    this.joinNodes = new JoinNodes(context);
  }

  /** The context for nodes. */
  public PlanContext getContext() {
    return context;
  }

  /** The join nodes for this graph's nodes. */
  public JoinNodes getJoinNodes() {
    return joinNodes;
  }

  /**
   * Adds a root node.
   * A root is a node that reaches a large number of other
   * nodes so is a natural place to start a traversal.
   * A root is not necessarily executed before other nodes.
   */
  public void addRoot(PlanGraphNode<?> root) {
    this.roots.add(Preconditions.checkNotNull(root));
  }

  /**
   * Reads state-vectors and reconstitutes them.
   */
  public void readFrom(ObjectInputStream in)
  throws IOException {
    Object read;
    try {
      read = in.readObject();
    } catch (ClassNotFoundException ex) {
      throw new IOException("Failed to deserialize plan graph", ex);
    }
    @SuppressWarnings("unchecked")  // We walk the list so will find any faults.
    ImmutableList<SerialNode> serialNodes = (ImmutableList<SerialNode>) read;
    int n = serialNodes.size();
    PlanGraphNode<?>[] unpacked = new PlanGraphNode<?>[n];
    for (int i = 0; i < n; ++i) {
      SerialNode sn = serialNodes.get(i);
      unpacked[i] = sn.sv.reconstitute(context, joinNodes);
    }
    // Double check that all the non-root nodes have an incoming edge.
    int[] nIncoming = new int[n];
    // Now that we've created the nodes, link them.
    for (int i = 0; i < n; ++i) {
      SerialNode sn = serialNodes.get(i);
      PlanGraphNode<?> pn = unpacked[i];
      for (int followerIndex : sn.followers) {
        // Cycles could happen here.
        pn.addFollower(unpacked[followerIndex]);
        ++nIncoming[followerIndex];
      }
    }
    // Make sure roots are recognized as such.
    for (int i = 0; i < n; ++i) {
      SerialNode sn = serialNodes.get(i);
      PlanGraphNode<?> pn = unpacked[i];
      if (sn.isRoot) {
        this.roots.add(pn);
      } else if (nIncoming[i] == 0) {
        throw new IllegalStateException(
            pn + " has no incoming edges and is not a root");
      }
    }
  }

  /**
   * Writes the graph to out.  The written form is independent of the
   * {@link PlanContext}.
   *
   * @see #readFrom
   */
  public void writeTo(ObjectOutputStream out) throws IOException {
    IdentityHashMap<PlanGraphNode<?>, Integer> nodeToIndex = findAllNodes();
    SerialNode[] serialNodes = new SerialNode[nodeToIndex.size()];
    for (Map.Entry<PlanGraphNode<?>, Integer> e : nodeToIndex.entrySet()) {
      PlanGraphNode<?> node = e.getKey();
      ImmutableList<PlanGraphNode<?>> followers = node.getFollowerList();
      int[] followerIndices = new int[followers.size()];
      for (int i = followerIndices.length; --i >= 0;) {
        followerIndices[i] = nodeToIndex.get(followers.get(i));
      }
      serialNodes[e.getValue()] =
          new SerialNode(
              node.getStateVector(), followerIndices, roots.contains(node));
    }
    out.writeObject(ImmutableList.copyOf(serialNodes));
  }


  private IdentityHashMap<PlanGraphNode<?>, Integer> findAllNodes() {
    IdentityHashMap<PlanGraphNode<?>, Integer> nodeToIndex =
        Maps.newIdentityHashMap();
    for (PlanGraphNode<?> root : effectiveRoots()) {
      findAllNodes(root, nodeToIndex);
    }
    return nodeToIndex;
  }


  private void findAllNodes(
      PlanGraphNode<?> node,
      IdentityHashMap<PlanGraphNode<?>, Integer> nodeToIndex) {
    if (!nodeToIndex.containsKey(node)) {
      nodeToIndex.put(node, nodeToIndex.size());
      for (PlanGraphNode<?> f : node.getFollowerList()) {
        findAllNodes(f, nodeToIndex);
      }
    }
  }

  /**
   * Execute the plan.
   */
  public void execute() throws IOException, MojoExecutionException {
    joinNodes.realizePipelineConstraints();

    // Create a reverse mapping from plan graph nodes to preceders.
    ReverseAdjacencyMap reverseMap = new ReverseAdjacencyMap();

    ImmutableSet.Builder<File> outputFilesToMark = ImmutableSet.builder();

    // Now, we can pick nodes that have zero incoming edges.
    // Each time through, we
    // 1. Check whether it has updates.
    // 2. If so, process it.
    // 3. Rebuild the follower list.
    // 4. If there are changes to the follower list, reflect them in reverseMap.
    try {
      for (PlanGraphNode<?> next; (next = reverseMap.getSatisfied()) != null;) {
        context.log.debug("Executing " + next);

        if (next.hasChangedInputs()) {
          context.log.debug(". Processing " + next);
          next.processInputs();
        }

        Optional<? extends Iterable<? extends PlanGraphNode<?>>> newFollowers;
        newFollowers = next.rebuildFollowersList(joinNodes);

        if (newFollowers.isPresent()) {
          reverseMap.replaceFollowers(
              next, ImmutableList.copyOf(newFollowers.get()));
        }
        reverseMap.markExecuted(next);
      }
    } finally {
      // Do this even on abnormal execution so that the IDE does not lose track
      // of changes that happened before a build failed suddenly.
      for (File outputFileToMark : outputFilesToMark.build()) {
        context.buildContext.refresh(outputFileToMark);
      }
    }
  }

  Iterable<PlanGraphNode<?>> effectiveRoots() {
    return Iterables.concat(roots, joinNodes.allJoinNodes());
  }

  boolean isEffectiveRoot(PlanGraphNode<?> node) {
    // Spuriously true iff node is a join node from another JoinNodes instance.
    return node instanceof JoinNodes.JoinPlanGraphNode || roots.contains(node);
  }



  static final class SerialNode implements Serializable {
    private static final long serialVersionUID = 1L;

    final PlanGraphNode.StateVector sv;
    final int[] followers;
    final boolean isRoot;

    SerialNode(PlanGraphNode.StateVector sv, int[] followers, boolean isRoot) {
      this.sv = sv;
      this.followers = followers.clone();
      this.isRoot = isRoot;
    }
  }


  /**
   * Adjacency relationships between nodes that maps nodes to the nodes that
   * precede it, and which associates some metadata with each node.
   */
  final class ReverseAdjacencyMap {
    /**
     * A relationship x -> Y such that there is an (x,y) whenever and only when
     * x.getFollowerList().contains(y) and x is reachable from a root or
     * effective root (join node) of the plan graph.
     */
    final IdentityHashMap<PlanGraphNode<?>, SatInfo> adj =
        Maps.newIdentityHashMap();

    final LinkedList<PlanGraphNode<?>> satisfied = Lists.newLinkedList();

    ReverseAdjacencyMap() {
      Set<PlanGraphNode<?>> traversed =
          Sets.<PlanGraphNode<?>>newIdentityHashSet();
      for (PlanGraphNode<?> root : effectiveRoots()) {
        build(root, traversed);
      }
      for (Map.Entry<PlanGraphNode<?>, SatInfo> e : adj.entrySet()) {
        if (e.getValue().unsatisfied == 0) {
          maybeAddSatisfied(e.getKey());
        }
      }
    }

    private void maybeAddSatisfied(PlanGraphNode<?> satNode) {
      SatInfo si = adj.get(satNode);
      Preconditions.checkState(si.unsatisfied == 0);
      if (satNode instanceof JoinNodes.JoinPlanGraphNode) {
        // join nodes tend to get created early before their preceders.
        // We enqueue them after any other nodes.
        satisfied.add(satNode);
      } else {
        // Enqueue before any join nodes.
        ListIterator<PlanGraphNode<?>> li =
            satisfied.listIterator(satisfied.size());
        while (li.hasPrevious()
               && li.previous() instanceof JoinNodes.JoinPlanGraphNode) {
          // Go back
        }
        if (li.hasPrevious() && li.hasNext()) {
          li.next();
        }
        li.add(satNode);
      }
    }

    public void markExecuted(PlanGraphNode<?> node) {
      SatInfo nodeSI = adj.get(node);
      Preconditions.checkState(!nodeSI.executed);
      nodeSI.executed = true;

      for (PlanGraphNode<?> follower : node.getFollowerList()) {
        SatInfo followerSI = adj.get(follower);
        Preconditions.checkState(
            !followerSI.executed && followerSI.unsatisfied > 0);
        --followerSI.unsatisfied;
        if (followerSI.unsatisfied == 0) {
          maybeAddSatisfied(follower);
        }
      }
    }

    public PlanGraphNode<?> getSatisfied() {
      PlanGraphNode<?> satisfiedNode = this.satisfied.poll();
      if (satisfiedNode == null) {
        boolean unexecuted = false;
        for (Map.Entry<PlanGraphNode<?>, SatInfo> e : adj.entrySet()) {
          if (!e.getValue().executed) {
            context.log.warn(
                "Unexecuted node " + e.getKey() + " is not satisfied");
            unexecuted = true;
          }
        }
        Preconditions.checkState(!unexecuted);
      }

      return satisfiedNode;
    }

    void build(PlanGraphNode<?> node, Set<PlanGraphNode<?>> traversed) {
      if (!traversed.add(node)) { return; }
      if (!adj.containsKey(node)) {
        adj.put(node, new SatInfo());
      }
      for (PlanGraphNode<?> follower : node.getFollowerList()) {
        buildFollower(node, follower, traversed);
      }
    }

    void buildFollower(
        PlanGraphNode<?> node, PlanGraphNode<?> follower,
        Set<PlanGraphNode<?>> traversed) {
      SatInfo followerSI = adj.get(follower);
      if (followerSI == null) {
        adj.put(follower, followerSI = new SatInfo());
      }

      boolean added = followerSI.preceders.add(node);
      Preconditions.checkState(added);

      if (!adj.get(node).executed) {
        if (followerSI.preceders.size() > 1 && followerSI.unsatisfied == 0) {
          // There was already something on the list, and unsatisfied is 0
          // meaning we're in the middle of execution and we just found an
          // unsatisfied prerequisite for follower.
          Preconditions.checkState(!followerSI.executed);
          satisfied.removeLastOccurrence(follower);
        }
        followerSI.unsatisfied += 1;
      }

      build(follower, traversed);
    }

    void unbuildFollower(PlanGraphNode<?> node, PlanGraphNode<?> follower) {
      SatInfo si = adj.get(follower);

      // This should only have been called by traversing followers from a
      // node that has not been marked satisfied, so sat count should be
      // > 0 for all followers
      Preconditions.checkState(
          !satisfied.contains(follower)
          && si.unsatisfied > 0);

      boolean removed = si.preceders.remove(node);
      Preconditions.checkState(removed);

      if (!adj.get(node).executed) {
        --si.unsatisfied;
      }
      if (si.preceders.isEmpty() && !isEffectiveRoot(follower)) {
        // No longer part of the graph.
        adj.remove(follower);
        // Remove the rest of the graph that is only reachable from follower.
        ImmutableList<PlanGraphNode<?>> followersOfRemoved =
            follower.getFollowerList();
        for (PlanGraphNode<?> f : followersOfRemoved) {
          unbuildFollower(follower, f);
        }
      } else {
        // There are other preceders, so decrement the unsatisfied count and
        // maybe add it to the satisfied list.
        if (si.unsatisfied == 0) {
          maybeAddSatisfied(follower);
        }
      }
    }

    void replaceFollowers(
        PlanGraphNode<?> node,
        ImmutableList<PlanGraphNode<?>> newFollowerList) {
      Set<PlanGraphNode<?>> newFollowerSet = Sets.newIdentityHashSet();
      newFollowerSet.addAll(newFollowerList);
      Preconditions.checkState(newFollowerSet.size() == newFollowerList.size());

      ImmutableList<PlanGraphNode<?>> oldFollowerList = node.getFollowerList();
      Set<PlanGraphNode<?>> oldFollowerSet = Sets.newIdentityHashSet();
      oldFollowerSet.addAll(oldFollowerList);
      Preconditions.checkState(oldFollowerSet.size() == oldFollowerList.size());

      for (PlanGraphNode<?> oldFollower : oldFollowerList) {
        if (newFollowerSet.contains(oldFollower)) { continue; }
        unbuildFollower(node, oldFollower);
      }

      Set<PlanGraphNode<?>> traversed = Sets.newIdentityHashSet();
      traversed.addAll(adj.keySet());

      node.setFollowerList(newFollowerList);
      for (PlanGraphNode<?> newFollower : newFollowerList) {
        buildFollower(node, newFollower, traversed);
      }
    }
  }

  static final class SatInfo {
    final List<PlanGraphNode<?>> preceders = Lists.newArrayList();
    /**
     * Count of preceders that have not been executed.  When this is zero, the
     * key node is ready for execution.
     */
    int unsatisfied;
    /**
     * True iff the key node has been executed.
     */
    boolean executed;

    @Override
    public String toString() {
      return preceders
          + (executed ? " executed" : "")
          + (unsatisfied != 0 ? " unsatisfied=" + unsatisfied : "");
    }
  }
}
