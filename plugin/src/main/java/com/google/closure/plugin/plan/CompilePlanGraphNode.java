package com.google.closure.plugin.plan;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.google.closure.plugin.common.StructurallyComparable;
import com.google.closure.plugin.common.Identifiable;
import com.google.closure.plugin.plan.BundlingPlanGraphNode.OptionsAndBundles;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

/**
 * A plan node that receives bundles from a {@link BundlingPlanGraphNode} and
 * compiles them to produce outputs.
 * <p>
 * The choice of whether to compile one at a time or all at once is left up to
 * the subclass.
 */
public abstract class CompilePlanGraphNode<
    O extends Serializable & StructurallyComparable & Identifiable,
    B extends BundlingPlanGraphNode.Bundle>
extends PlanGraphNode<CompilePlanGraphNode.CompileStateVector<O, B>> {

  /** Options for compiler. */
  public Optional<Update<OptionsAndBundles<O, B>>> optionsAndBundles;
  protected final Map<B, ImmutableList<File>> bundleToOutputs =
      Maps.newLinkedHashMap();
  protected final List<File> changedFiles = Lists.newArrayList();

  protected CompilePlanGraphNode(PlanContext context) {
    super(context);
  }

  /** Called with preceders to allow. */
  @Override
  protected void preExecute(Iterable<? extends PlanGraphNode<?>> preceders) {
    this.optionsAndBundles = Optional.absent();
    for (PlanGraphNode<?> p : preceders) {
      if (p instanceof BundlingPlanGraphNode<?, ?>) {
        @SuppressWarnings("unchecked")  // TODO: unsound
        BundlingPlanGraphNode<O, B> bundler = (BundlingPlanGraphNode<O, B>) p;
        Preconditions.checkState(!optionsAndBundles.isPresent());
        this.optionsAndBundles = bundler.getOptionsAndBundles();
      }
    }
    Preconditions.checkState(optionsAndBundles.isPresent());
  }

  /**
   * By default, nothing to do since the bundler has done the work of
   * triaging bundles.
   */
  @Override
  protected void filterUpdates() throws IOException {
    // Done
  }

  /**
   * After processing, the list of files that were changed.
   */
  @Override
  protected Iterable<? extends File> changedOutputFiles() {
    return ImmutableList.copyOf(this.changedFiles);
  }


  protected void processDefunctBundles(
      Optional<Update<OptionsAndBundles<O, B>>> obs) {
    if (obs.isPresent()) {
      for (OptionsAndBundles<O, B> ob : obs.get().defunct) {
        for (B b : ob.bundles) {
          ImmutableList<File> filesForBundle = this.bundleToOutputs.remove(b);
          if (filesForBundle != null) {
            for (File f : filesForBundle) {
              try {
                deleteIfExists(f);
              } catch (IOException ex) {
                context.log.error("Failed to delete " + f, ex);
              }
            }
          }
        }
      }
    }
  }

  /** Deletes the file and notifies the build context of that. */
  protected void deleteIfExists(File f) throws IOException {
    if (f != null && f.exists()) {
      if (!f.delete()) {
        throw new IOException("Failed to delete " + f);
      }
      this.changedFiles.add(f);
    }
  }


  /**
   * Copy files from one directory root to another.
   * Typically this is used to allow building files to a temp directory and,
   * on success, copying over to the final output directory.
   * <p>
   * In addition to manipulating the file system, this updates
   * {@link #changedFiles} list.
   *
   * @param from file or directory tree to copy from.
   * @param to file or directory tree to copy to.
   * @param out receives files that have changed.  May be used to rebuild
   *     the relevant {@link #bundleToOutputs} entry.
   */
  protected void copyFilesOver(
      File from, File to, ImmutableSet.Builder<File> out) throws IOException {
    if (from.isDirectory()) {
      java.nio.file.Files.createDirectories(to.toPath());
      String[] children = from.list();
      if (children != null) {
        for (String child : children) {
          File fromChild = new File(from, child);
          File toChild = new File(to, child);
          copyFilesOver(fromChild, toChild, out);
        }
      }
    } else {
      if (!(to.exists() && Files.equal(from, to))) {
        java.nio.file.Files.move(from.toPath(), to.toPath());
        changedFiles.add(to);
      }
      out.add(to);
    }
  }


  /** State vector for a compiler. */
  public static abstract class CompileStateVector<
      O extends Serializable & StructurallyComparable & Identifiable,
      B extends BundlingPlanGraphNode.Bundle>
  implements PlanGraphNode.StateVector {
    private static final long serialVersionUID = 1;

    final Optional<Update<OptionsAndBundles<O, B>>> optionsAndBundles;
    final ImmutableMap<B, ImmutableList<File>> bundleToOutputs;
    final ImmutableList<File> changedFiles;

    protected CompileStateVector(CompilePlanGraphNode<O, B> node) {
      this.optionsAndBundles = node.optionsAndBundles;
      this.bundleToOutputs = ImmutableMap.copyOf(node.bundleToOutputs);
      this.changedFiles = ImmutableList.copyOf(node.changedFiles);
    }

    protected <N extends CompilePlanGraphNode<O, B>>
    N apply(N node) {
      node.changedFiles.clear();
      node.bundleToOutputs.putAll(bundleToOutputs);
      node.optionsAndBundles = optionsAndBundles;
      return node;
    }
  }
}
