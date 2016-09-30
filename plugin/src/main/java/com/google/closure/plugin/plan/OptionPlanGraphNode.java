package com.google.closure.plugin.plan;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.sonatype.plexus.build.incremental.BuildContext;

import com.google.closure.plugin.common.DirectoryScannerSpec;
import com.google.closure.plugin.common.Identifiable;
import com.google.closure.plugin.common.Options;
import com.google.closure.plugin.common.SourceOptions;
import com.google.closure.plugin.common.Sources;
import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.common.StructurallyComparable;
import com.google.closure.plugin.common.TypedFile;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * A plan graph node that is provisioned with plexus-configured {@link Options}
 * or immutable configuration objects derived from them.
 * <p>
 * These graph nodes typically are root nodes and only follow join nodes.
 * <p>
 * They produce {@link OptionPlanGraphNode.OptionsAndInputs} which are consumed
 * by {@link BundlingPlanGraphNode}.
 *
 * @param <O> bundle of options
 */
public abstract
class OptionPlanGraphNode<
O extends Serializable & StructurallyComparable & Identifiable>
extends PlanGraphNode<OptionPlanGraphNode.OptionStateVector<O>> {

  private final List<O> optionSets = Lists.newArrayList();
  private Optional<Update<OptionsAndInputs<O>>> updates = Optional.absent();

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
      optionSets.add(Preconditions.checkNotNull(newOptionSet));
    }
  }

  /** The option sets available. */
  public List<O> getOptionSets() {
    return Collections.unmodifiableList(optionSets);
  }

  /**
   * The options and their sources based.
   * These are considered changed when the input file set has changes.
   */
  public Optional<Update<OptionsAndInputs<O>>> getUpdates() {
    return updates;
  }

  /** Does nothing by default since preceders are typically just join nodes. */
  @Override
  protected void preExecute(Iterable<? extends PlanGraphNode<?>> preceders) {
    // nop
  }

  /**
   * By default, does nothing since options nodes aren't in the business of
   * parsing inputs.
   */
  @Override
  protected void process() throws IOException, MojoExecutionException {
    // nop
  }


  /**
   * This default implementation assumes that {@code <O extends SourceOptions>}.
   */
  protected DirectoryScannerSpec getScannerSpecForOptions(O opts) {
    return ((SourceOptions) opts).toDirectoryScannerSpec(context);
  }

  @Override
  protected void filterUpdates() throws IOException {
    ImmutableList.Builder<OptionsAndInputs<O>> unchanged =
        ImmutableList.builder();
    ImmutableList.Builder<OptionsAndInputs<O>> changed =
        ImmutableList.builder();
    ImmutableList.Builder<OptionsAndInputs<O>> defunct =
        ImmutableList.builder();

    Map<O, OptionsAndInputs<O>> optionsToInputs = Maps.newLinkedHashMap();
    boolean isIncremental = context.buildContext.isIncremental();
    if (isIncremental && this.updates.isPresent()) {
      for (OptionsAndInputs<O> old : (
          ImmutableList.<OptionsAndInputs<O>>builder()
          .addAll(updates.get().unchanged)
          .addAll(updates.get().changed)
          .build())) {
        optionsToInputs.put(old.options, old);
      }
    }

    for (O options : this.optionSets) {
      DirectoryScannerSpec spec = getScannerSpecForOptions(options);
      OptionsAndInputs<O> old = optionsToInputs.remove(options);
      boolean specChanged = false;
      if (old != null) {  // Implies incremental
        BuildContext buildContext = context.buildContext;
        for (TypedFile root : spec.roots) {
          ImmutableList<Source> changedFiles = spec.scan(
              buildContext.newScanner(root.f, false), root.ps);
          if (!changedFiles.isEmpty()) {
            specChanged = true;
          }
          if (!specChanged) {
            ImmutableList<Source> deletedFiles = spec.scan(
                buildContext.newDeleteScanner(root.f), root.ps);
            if (!deletedFiles.isEmpty()) {
              specChanged = true;
            }
          }
          if (specChanged) { break; }
        }
      } else {
        specChanged = true;  // conservatively
      }
      if (specChanged) {
        Sources sources = Sources.scan(context.log, spec);
        changed.add(new OptionsAndInputs<>(options, sources.sources));
      } else {
        unchanged.add(Preconditions.checkNotNull(old));
      }
    }

    defunct.addAll(optionsToInputs.values());

    this.updates = Optional.of(new Update<>(
        unchanged.build(),
        changed.build(),
        defunct.build()));
  }

  /**
   * By default, this changes no files.
   */
  @Override
  protected Iterable<? extends File> changedOutputFiles() {
    return ImmutableList.of();
  }

  /**
   * A state vector that contains one or more sets of options.
   */
  @SuppressWarnings("synthetic-access")
  public static abstract class OptionStateVector<
      O extends Serializable & StructurallyComparable & Identifiable>
  implements PlanGraphNode.StateVector {

    private static final long serialVersionUID = 1L;

    /** The option sets that are fanned-out to followers. */
    final ImmutableList<O> optionSets;

    final Optional<Update<OptionsAndInputs<O>>> updates;

    protected OptionStateVector(OptionPlanGraphNode<O> node) {
      this.optionSets = ImmutableList.copyOf(node.optionSets);
      this.updates = node.updates;
    }

    protected <N extends OptionPlanGraphNode<O>>
    N apply(N node) {
      node.setOptionSets(optionSets);
      ((OptionPlanGraphNode<O>) node).updates = updates;
      return node;
    }
  }


  /** An options instance and the sources it specifies. */
  public static final class OptionsAndInputs<
      O extends Serializable & StructurallyComparable & Identifiable>
  implements Serializable, StructurallyComparable, Identifiable {
    private static final long serialVersionUID = 1L;

    /** */
    public final O options;
    /** Sources for the compile specified by options. */
    public final ImmutableList<Source> sources;

    OptionsAndInputs(O options, Iterable<? extends Source> sources) {
      this.options = options;
      this.sources = ImmutableList.copyOf(sources);
    }

    @Override
    public String getId() {
      return options.getId();
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((options == null) ? 0 : options.hashCode());
      result = prime * result + ((sources == null) ? 0 : sources.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      OptionsAndInputs<?> other = (OptionsAndInputs<?>) obj;
      if (options == null) {
        if (other.options != null) {
          return false;
        }
      } else if (!options.equals(other.options)) {
        return false;
      }
      if (sources == null) {
        if (other.sources != null) {
          return false;
        }
      } else if (!sources.equals(other.sources)) {
        return false;
      }
      return true;
    }
  }
}
