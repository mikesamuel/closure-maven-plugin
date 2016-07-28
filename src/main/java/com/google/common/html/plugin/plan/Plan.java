package com.google.common.html.plugin.plan;

import java.io.IOException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * A plan that executes steps by looking at which inputs have been
 * satisfied.
 */
public class Plan {

  private static final class StepPlanState {
    final Step step;
    int nUnsatisfiedInputs;

    StepPlanState(Step step) {
      this.step = step;
    }
  }

  private final Log log;
  private final HashStore hashStore;
  private final List<Step> ready = Lists.newArrayList();
  /**
   * For each step source, the count of unexecuted steps that have it as an
   * output.
   */
  private final EnumMap<StepSource, Integer> outputUsageCount =
      Maps.newEnumMap(StepSource.class);
  /**
   * Fpr each step source, the unready steps that have it as an input.
   */
  private final Multimap<StepSource, StepPlanState> unexecuted =
      HashMultimap.create();

  /**
   * @param hashStore used to decide whether to execute or skip a step.
   * @param steps to execute.
   */
  public Plan(Log log, HashStore hashStore, Iterable<? extends Step> steps) {
    this.log = log;
    this.hashStore = hashStore;
    addSteps(steps);
  }

  private void addSteps(Iterable<? extends Step> steps) {
    for (Step s : steps) {
      // Increment usage counts since s is unexecuted.
      for (StepSource w : s.writes) {
        Integer oldUsageCount = outputUsageCount.get(w);
        outputUsageCount.put(
            w,
            1 + (oldUsageCount != null ? oldUsageCount.intValue() : 0));
      }
      // Figure out whether the inputs are satisfied or not.
      EnumSet<StepSource> unsatisfied = EnumSet.noneOf(StepSource.class);
      for (StepSource r : s.reads) {
        Integer usageCount = outputUsageCount.get(r);
        if (usageCount != null && usageCount.intValue() != 0) {
          unsatisfied.add(r);
        }
      }
      // Either enqueue as ready or record the information we need to decide
      // when to enqueue.
      if (unsatisfied.isEmpty()) {
        ready.add(s);
      } else {
        StepPlanState ss = new StepPlanState(s);
        ss.nUnsatisfiedInputs = unsatisfied.size();
        for (StepSource r : unsatisfied) {
          unexecuted.put(r, ss);
        }
      }
    }
  }

  /** True if there are no more steps to execute. */
  public final boolean isComplete() {
    return unexecuted.isEmpty() && this.ready.isEmpty();
  }

  /**
   * Executes one step.
   */
  public final void executeOneStep() throws MojoExecutionException {
    if (ready.isEmpty()) {
      if (unexecuted.isEmpty()) {
        return;  // All done.
      }
      Set<StepPlanState> uniqUnexecuted = Sets.newIdentityHashSet();
      uniqUnexecuted.addAll(unexecuted.values());
      for (StepPlanState s : uniqUnexecuted) {
        boolean foundUnhashedInputs = false;
        for (Ingredient input : s.step.inputs) {
          Optional<Hash> inpHash;
          try {
            inpHash = input.hash();
            if (!inpHash.isPresent()) {
              foundUnhashedInputs = true;
              log.warn(
                  "Step " + s.step.key + " has unsatisfied input " + input.key);
            }
          } catch (IOException ex) {
            log.warn("Could not hash " + input.key + " for " + s.step.key, ex);
            foundUnhashedInputs = true;
          }
        }
        if (!foundUnhashedInputs) {
          log.warn("Step " + s.step.key + " could not be scheduled");
        }
      }
      throw new MojoExecutionException(
          "All remaining plan steps have unsatisfied inputs");
    }
    Step step = ready.remove(0);

    // Sanity check that ready really is satisfied.
    EnumSet<StepSource> readsThatNeedToBeWritten =
        EnumSet.noneOf(StepSource.class);
    for (StepSource ss : step.reads) {
      Integer count = this.outputUsageCount.get(ss);
      if (count != null && 0 != count.intValue()) {
        readsThatNeedToBeWritten.add(ss);
      }
    }
    if (!readsThatNeedToBeWritten.isEmpty()) {
      throw new MojoExecutionException(
          "Cannot execute " + step.key
          + " because it was spuriously queued for execution despite having"
          + " unsatisfied reads: " + readsThatNeedToBeWritten);
      // TODO: maybe keep a set of reads assumed satisfied based on readiness
      // queuing and fail-fast in addSteps when something that writes those is
      // added.
    }

    // We need to rebuild if any of the inputs' or hashes don't match.
    boolean hashesOk = true;
    List<Hash> inputHashes = Lists.newArrayList();
    for (Ingredient inp : step.inputs) {
      Optional<Hash> inpHash = Optional.absent();
      try {
        inpHash = inp.hash();
      } catch (IOException ex) {
        log.warn("Failed to hash " + inp.key + " for " + step.key, ex);
      }
      if (inpHash.isPresent()) {
        inputHashes.add(inpHash.get());
      } else {
        hashesOk = false;
        log.warn("Missing hash for " + inp.key);
      }
    }

    boolean outputsExist = true;

    Optional<Hash> storedStepHash = hashStore.getHash(step.key);
    Hash stepHash = Hash.hashAllHashes(inputHashes);

    boolean reuse = hashesOk
        && storedStepHash.isPresent()
        && stepHash.equals(storedStepHash.get())
        && outputsExist;

    // Maybe rebuild.
    if (reuse) {
      log.debug("Reusing output of " + step.key);
    } else {
      log.debug("Executing " + step.key);
      step.execute(log);
      log.debug("Executed " + step.key);
    }

    // Add steps before updating satisfaction counts, so that we don't declare
    // something ready that will then have new inputs.
    addSteps(step.extraSteps(log));
    hashStore.setHash(step.key, stepHash);

    // Update dependency satisfaction counts and enqueue newly ready.
    for (StepSource w : step.writes) {
      // Decrement usage counts.
      int oldUsageCount = outputUsageCount.get(w);
      Preconditions.checkState(oldUsageCount > 0);
      int newUsageCount = oldUsageCount - 1;
      outputUsageCount.put(w, newUsageCount);

      if (newUsageCount == 0) {
        // Enqueue any unexecuted that are now ready.
        for (StepPlanState unready : this.unexecuted.removeAll(w)) {
          int unsatCount = unready.nUnsatisfiedInputs;
          Preconditions.checkState(unsatCount > 0);
          int newUnsatCount = unsatCount - 1;
          unready.nUnsatisfiedInputs = newUnsatCount;
          if (newUnsatCount == 0) {
            ready.add(unready.step);
          }
        }
      }
    }
  }
}