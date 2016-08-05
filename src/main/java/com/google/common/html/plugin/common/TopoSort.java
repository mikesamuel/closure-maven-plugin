package com.google.common.html.plugin.common;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;

/**
 * Sorts depencies before dependers.
 * <a href="https://en.wikipedia.org/wiki/Topological_sorting">Toposort wiki</a>
 *
 * @param <I> The type of a sortable item.
 * @param <D> The type of a dependency; a require or a provide.
 */
public final class TopoSort<I extends Comparable<? super I>,
                            D extends Comparable<? super D>> {
  private final ImmutableList<I> items;
  private final ImmutableMap<I, DepNode<I, D>> nodes;
  private final ImmutableList<I> orderedItems;

  /**
   * Performs a topological sort ordering dependencies before things that
   * depend upon them.
   *
   * @param getRequires relates an item to the symbols it requires.
   * @param getProvides relates an item to the symbols it provides.
   * @param ambientlyAvailable symbols that need not be provided by any
   *     item.
   */
  public TopoSort(
      Function<? super I, ? extends Iterable<? extends D>> getRequires,
      Function<? super I, ? extends Iterable<? extends D>> getProvides,
      Iterable<? extends I> itemsToSort,
      Set<? super D> ambientlyAvailable)
  throws MissingRequirementException, CyclicRequirementException {
    Map<I, DepNode<I, D>> nodesMap = Maps.newTreeMap();
    Multimap<D, DepNode<I, D>> byProvides = Multimaps.newListMultimap(
        Maps.<D, Collection<DepNode<I, D>>>newTreeMap(),
        new Supplier<List<DepNode<I, D>>>() {
          @Override
          public List<DepNode<I, D>> get() {
            return Lists.newArrayList();
          }
        });

    this.items = ImmutableList.copyOf(itemsToSort);
    for (I item : itemsToSort) {
      DepNode<I, D> node = new DepNode<>(
          item, ImmutableSortedSet.copyOf(getProvides.apply(item)));
      DepNode<I, D> prev = nodesMap.put(item, node);
      if (prev != null) {
        // Ensure uniq so that we don't double count later on.
        throw new IllegalArgumentException(
            "Duplicate item " + item + " and " + prev.key);
      }
      for (D prv : node.provides) {
        byProvides.put(prv, node);
      }
    }
    this.nodes = ImmutableMap.copyOf(nodesMap);

    Multimap<D, DepNode<I, D>> byRequires = Multimaps.newListMultimap(
        Maps.<D, Collection<DepNode<I, D>>>newTreeMap(),
        new Supplier<List<DepNode<I, D>>>() {
          @Override
          public List<DepNode<I, D>> get() {
            return Lists.newArrayList();
          }
        });

    LinkedList<DepNode<I, D>> satisfied = Lists.newLinkedList();

    for (DepNode<I, D> node : nodes.values()) {
      I item = node.key;

      for (D req : getRequires.apply(item)) {
        if (ambientlyAvailable.contains(req)) { continue; }

        byRequires.put(req, node);

        Collection<DepNode<I, D>> providers = byProvides.get(req);
        if (providers.isEmpty()) {
          throw new MissingRequirementException(
              item, req, null, null);
        }
        for (DepNode<I, D> provider : providers) {
          node.requires.add(new Edge<>(provider, req));
        }
        node.nUnsatisfied += providers.size();
      }
      node.nUnsatisfied = node.requires.size();
      // By adding in an itemList loop, we provide a stable-ish topo-sort.
      if (node.nUnsatisfied == 0) {
        satisfied.add(node);
      }
    }

    ImmutableList.Builder<I> orderedResultBuilder = ImmutableList.builder();
    while (!satisfied.isEmpty()) {
      DepNode<I, D> satNode = satisfied.removeFirst();
      orderedResultBuilder.add(satNode.key);
      for (D prv : satNode.provides) {
        for (DepNode<I, D> depender : byRequires.get(prv)) {
          Preconditions.checkState(depender.nUnsatisfied > 0);
          --depender.nUnsatisfied;
          if (depender.nUnsatisfied == 0) {
            satisfied.add(depender);
          }
        }
      }
    }

    this.orderedItems = orderedResultBuilder.build();
    if (orderedItems.size() != this.items.size()) {
      // Since we ruled out missing dependencies above, the only reason
      // something wouldn't be omitted is either
      // 1. A key that doesn't obey equals/compareTo contracts.
      // 2. A dependency cycle.
      // We optimistically assume that (1) doesn't happen.
      Set<I> seen = Sets.newTreeSet();
      List<Object> cycle = Lists.newArrayList();
      for (DepNode<I, D> node : byRequires.values()) {
        seen.clear();
        cycle.clear();
        checkForCycles(seen, cycle, node);
      }
      throw new AssertionError(
          "Failed to identify missing dependency or cycle");
    }
  }

  /** Gets all items in topo order. */
  public ImmutableList<I> getSortedItems() {
    return orderedItems;
  }

  /**
   * Gets all the dependencies of the given item in
   * {@link #getSortedItems() item order}.
   */
  public ImmutableList<I> getDependenciesTransitive(I k) {
    Set<I> allDeps = Sets.newTreeSet();
    addDeps(nodes.get(k), allDeps);
    if (allDeps.isEmpty()) { return ImmutableList.of(); }
    ImmutableList.Builder<I> depsInOrder = ImmutableList.builder();
    for (I item : orderedItems) {
      if (allDeps.contains(item)) {
        depsInOrder.add(item);
      }
    }
    return depsInOrder.build();
  }

  private void addDeps(DepNode<I, D> node, Set<I> allDeps) {
    for (Edge<I, D> edge : node.requires) {
      if (allDeps.add(edge.target.key)) {
        addDeps(edge.target, allDeps);
      }
    }
  }

  private static
  <I extends Comparable<? super I>, D extends Comparable<? super D>>
  void checkForCycles(Set<I> seen, List<Object> cycle, DepNode<I, D> node)
  throws CyclicRequirementException {
    if (node.nUnsatisfied == 0) {
      return;
    }
    cycle.add(node.key);
    if (!seen.add(node.key)) {
      throw new CyclicRequirementException(
          ImmutableList.copyOf(cycle), null, null);
    }
    for (Edge<I, D> follower : node.requires) {
      cycle.add(follower.value);
      checkForCycles(seen, cycle, follower.target);
      cycle.remove(cycle.size() - 1);
    }
    cycle.remove(cycle.size() - 1);
    seen.remove(node.key);
  }


  private static final
  class DepNode<I extends Comparable<? super I>,
                D extends Comparable<? super D>> {
    final I key;
    final List<Edge<I, D>> requires = Lists.newArrayList();
    final ImmutableSortedSet<D> provides;
    int nUnsatisfied;

    DepNode(I key, ImmutableSortedSet<D> provides) {
      this.key = key;
      this.provides = provides;
      // Wait until we know how many providers
      // there are to compute nUnsatisfied.
    }
  }

  private static final
  class Edge<I extends Comparable<? super I>,
             D extends Comparable<? super D>> {
    final DepNode<I, D> target;
    final D value;

    Edge(DepNode<I, D> target, D value) {
      this.target = target;
      this.value = value;
    }
  }

  /** Raised when a dependency requires a key that is never provided. */
  public static class MissingRequirementException extends Exception {
    private static final long serialVersionUID = -3770103884142545342L;

    /** The item requiring {@link #required}. */
    public final Object requirer;

    /** The unsatisfied dependency. */
    public final Object required;

    /**
     * @param requirer the item whose dependency is not satisfied.
     * @param required The unsatisfied dependency.
     */
    public MissingRequirementException(
        Object requirer, Object required, String msg, Throwable th) {
      super(
          msg != null ? msg : requirer + " is missing requirement " + required,
          th);
      this.requirer = requirer;
      this.required = required;
    }
  }

  /**
   * Raised when a dependency must provide its declarations before it can be
   * loaded.
   */
  public static class CyclicRequirementException extends Exception {
    private static final long serialVersionUID = -5794209553363250351L;

    /** The dependency keys on the cycle. */
    public final ImmutableList<Object> cycle;

    /**
     * @param cycle The dependency keys on the cycle.
     */
    public CyclicRequirementException(
        Iterable<?> cycle, String msg, Throwable th) {
      super(
          msg != null ? msg : "Cycle : " + ImmutableList.copyOf(cycle),
          th);
      this.cycle = ImmutableList.copyOf(cycle);
    }
  }
}
