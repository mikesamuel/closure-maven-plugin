package com.google.closure.plugin.plan;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.CharSink;
import com.google.common.io.Files;

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
      } else if (nIncoming[i] == 0 && !isEffectiveRoot(pn)) {
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
    this.roots.addAll(joinNodes.realizePipelineConstraints());

    ReverseAdjacencyMap reverse = new ReverseAdjacencyMap();
    ImmutableList<PlanGraphNode<?>> executionOrder =
        reverse.computeExecutionOrder();
    if (context.log.isDebugEnabled()) {
      context.log.info("Execution order");
      for (int i = 0, n = executionOrder.size(); i < n; ++i) {
        context.log.info(
            ". " + i + ": " + executionOrder.get(i).getClass().getName()
            .replaceFirst("^.*[.]([^.]*[.][^.]*)$", "$1"));
      }
    }

    String dotOutput = System.getProperty("closure.plugin.dotout");
    if (dotOutput != null) {
      dumpDotGraph(
          reverse.adj, Files.asCharSink(new File(dotOutput), Charsets.UTF_8));
    }

    if (context.log.isDebugEnabled()) {
      if (requireNoCycles()) {
        throw new MojoExecutionException("Graph cycle detected");
      }
    }

    Set<File> changedOutputs = Sets.newLinkedHashSet();
    try {
      for (PlanGraphNode<?> next : executionOrder) {
        context.log.debug("Executing " + next);

        next.preExecute(reverse.getPreceders(next));
        next.filterUpdates();
        // TODO: we need to systematically remove messages from files that are
        // about to be processed.
        next.process();
        for (File changed : next.changedOutputFiles()) {
          changedOutputs.add(changed);
        }
      }
    } finally {
      // Do this even on abnormal execution so that the IDE does not lose track
      // of changes that happened before a build failed suddenly.
      for (File changed : changedOutputs) {
        context.buildContext.refresh(changed);
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
    final IdentityHashMap<PlanGraphNode<?>, List<PlanGraphNode<?>>> adj =
        Maps.newIdentityHashMap();

    ReverseAdjacencyMap() {
      Set<PlanGraphNode<?>> traversed =
          Sets.<PlanGraphNode<?>>newIdentityHashSet();
      for (PlanGraphNode<?> root : effectiveRoots()) {
        build(root, traversed);
      }
    }

    Iterable<? extends PlanGraphNode<?>> getPreceders(PlanGraphNode<?> n) {
      return ImmutableList.copyOf(this.adj.get(n));
    }

    void dump(Appendable out) throws IOException {
      out.append("FORWARD MAP\n");
      for (PlanGraphNode<?> n : this.adj.keySet()) {
        Collection<PlanGraphNode<?>> adjToN = n.getFollowerList();
        out.append(". ").append(n.toString())
           .append(" => ").append(adjToN.toString())
           .append('\n');
      }
      out.append('\n');

      out.append("REVERSE MAP\n");
      for (PlanGraphNode<?> n : this.adj.keySet()) {
        List<PlanGraphNode<?>> adjToN = adj.get(n);
        out.append(". ").append(n.toString())
           .append(" => ").append(adjToN.toString())
           .append('\n');
      }
      out.append('\n');
    }

    void build(PlanGraphNode<?> node, Set<PlanGraphNode<?>> traversed) {
      if (!traversed.add(node)) { return; }
      if (!adj.containsKey(node)) {
        adj.put(node, Lists.<PlanGraphNode<?>>newArrayList());
      }
      for (PlanGraphNode<?> follower : node.getFollowerList()) {
        buildFollower(node, follower, traversed);
      }
    }

    void buildFollower(
        PlanGraphNode<?> node, PlanGraphNode<?> follower,
        Set<PlanGraphNode<?>> traversed) {
      List<PlanGraphNode<?>> followerList = adj.get(follower);
      if (followerList == null) {
        adj.put(follower, followerList = Lists.newArrayList());
      }

      followerList.add(node);

      build(follower, traversed);
    }


    ImmutableList<PlanGraphNode<?>> computeExecutionOrder() {
      @SuppressWarnings("synthetic-access")
      IdentityHashMap<PlanGraphNode<?>, Integer> nodeToIndex = findAllNodes();
      int n = nodeToIndex.size();
      PlanGraphNode<?>[] indexToNode = new PlanGraphNode[n];

        // Count preceders and establish reverse map.
      int[] precederCount = new int[nodeToIndex.size()];
      for (Map.Entry<PlanGraphNode<?>, Integer> e : nodeToIndex.entrySet()) {
        PlanGraphNode<?> node = e.getKey();
        int index = e.getValue();
        Preconditions.checkState(indexToNode[index] == null);
        indexToNode[index] = node;
        for (PlanGraphNode<?> follower : node.getFollowerList()) {
          ++precederCount[nodeToIndex.get(follower)];
        }
      }

      // Iteratively move items with zero unqueued preceders on a ready list,
      // schedule a ready one, and then decrement the unsat count for its
      // followers.
      // This handles branching and joining properly.
      int[] unsatCount = precederCount.clone();
      ImmutableList.Builder<PlanGraphNode<?>> execOrder = ImmutableList.builder();
      Deque<PlanGraphNode<?>> ready = new ArrayDeque<>();
      for (int i = 0; i < n; ++i) {
        if (unsatCount[i] == 0) {
          ready.add(indexToNode[i]);
        }
      }

      for (PlanGraphNode<?> next; (next = ready.poll()) != null;) {
        execOrder.add(next);
        for (PlanGraphNode<?> follower : next.getFollowerList()) {
          int followerIndex = nodeToIndex.get(follower);
          Preconditions.checkState(unsatCount[followerIndex] > 0);
          --unsatCount[followerIndex];
          if (0 == unsatCount[followerIndex]) {
            ready.add(follower);
          }
        }
      }

      ImmutableList<PlanGraphNode<?>> completeExecOrder = execOrder.build();
      if (completeExecOrder.size() != n) {
        // There's a cycle somewhere.

      }
      return completeExecOrder;
    }
  }

  private boolean requireNoCycles() {
    List<PlanGraphNode<?>> path = Lists.<PlanGraphNode<?>>newArrayList();
    Set<PlanGraphNode<?>> inPath =
        Sets.<PlanGraphNode<?>>newIdentityHashSet();
    for (PlanGraphNode<?> root : this.effectiveRoots()) {
      if (requireNoCyclesFrom(root, path, inPath)) {
        return true;
      }
    }
    return false;
  }

  private boolean requireNoCyclesFrom(
      PlanGraphNode<?> n, List<PlanGraphNode<?>> path,
      Set<PlanGraphNode<?>> inPath) {
    path.add(n);
    if (!inPath.add(n)) {
      List<PlanGraphNode<?>> cycle = path.subList(
          path.indexOf(n), path.size());
      Preconditions.checkState(cycle.size() >= 2);
      context.log.error("Cycle in dependency graph : " + cycle);
      return true;
    }
    for (PlanGraphNode<?> follower : n.getFollowerList()) {
      if (requireNoCyclesFrom(follower, path, inPath)) {
        return true;
      }
    }
    inPath.remove(n);
    path.remove(path.size() - 1);
    return false;
  }

  void dumpDotGraph(
      IdentityHashMap<PlanGraphNode<?>, List<PlanGraphNode<?>>> adj,
      CharSink sink) {
    try (Writer out = sink.openBufferedStream()) {
      out.write("digraph planGraph {\n");
      Set<PlanGraphNode<?>> visited = Sets.newIdentityHashSet();
      Iterable<PlanGraphNode<?>> effectiveRoots = effectiveRoots();
      for (PlanGraphNode<?> root : effectiveRoots) {
        out.write("  root -> " + dotName(root) + " [color=blue];\n");
      }
      writeForwardGraph(effectiveRoots, visited, out);
      for (Map.Entry<PlanGraphNode<?>, List<PlanGraphNode<?>>> e
           : adj.entrySet()) {
        PlanGraphNode<?> n = e.getKey();
        List<PlanGraphNode<?>> preceders = e.getValue();
        String name = dotName(n);
        if (preceders.isEmpty()) {
          if (!visited.contains(n)) {
            out.write("  " + name + ";\n");
          }
        } else {
          for (PlanGraphNode<?> p : preceders) {
            out.write("  " + dotName(p) + " -> " + name + " [color=red];\n");
          }
        }
      }
      out.write("}\n");
    } catch (IOException ex) {
      context.log.error("Failed to write dot graph", ex);
    }
  }

  private static void writeForwardGraph(
      Iterable<? extends PlanGraphNode<?>> nodes, Set<PlanGraphNode<?>> visited,
      Writer out) throws IOException {
    for (PlanGraphNode<?> n : nodes) {
      if (!visited.add(n)) {
        continue;
      }
      String nodeName = dotName(n);
      ImmutableList<PlanGraphNode<?>> followers = n.getFollowerList();
      if (followers.isEmpty()) {
        out.write("  " + nodeName + ";\n");
      } else {
        for (PlanGraphNode<?> f : followers) {
          out.write("  " + nodeName + " -> " + dotName(f) + " [color=blue];\n");
        }
      }
      writeForwardGraph(followers, visited, out);
    }
  }

  private static String dotName(PlanGraphNode<?> n) {
    return "\"" + n + "@" + Integer.toHexString(System.identityHashCode(n))
        + "\"";
  }
}
