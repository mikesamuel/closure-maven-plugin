package com.google.common.html.plugin.plan;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;

/**
 * A plan that executes steps by looking at which inputs have been
 * satisfied.
 */
public class Plan {

  static final class StepPlanState {
    final Step step;
    int nUnsatisfiedInputs;

    StepPlanState(Step step) {
      this.step = step;
    }
  }


  private final Log log;
  private final HashStore hashStore;
  private final Multimap<String, StepPlanState> byInput =
      Multimaps.<String, StepPlanState>newListMultimap(
          Maps.<String, Collection<StepPlanState>>newLinkedHashMap(),
          new Supplier<List<StepPlanState>>() {
            public List<StepPlanState> get() {
              return Lists.newArrayList();
            }
          });
  private final List<StepPlanState> ready = Lists.newArrayList();
  private final Set<StepPlanState> unexecuted = Sets.newLinkedHashSet();

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
      StepPlanState ss = new StepPlanState(s);
      ss.nUnsatisfiedInputs = s.inputs.size();
      for (Ingredient inp : s.inputs) {
        byInput.put(inp.key, ss);
        Optional<Hash> inpHash = Optional.absent();
        try {
          inpHash = inp.hash();
        } catch (IOException ex) {
          log.warn("Could not hash " + inp.key + " to " + s.key, ex);
        }
        if (inpHash.isPresent()) {
          --ss.nUnsatisfiedInputs;
        }
      }
      if (ss.nUnsatisfiedInputs == 0) {
        ready.add(ss);
      } else {
        unexecuted.add(ss);
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
      for (StepPlanState s : unexecuted) {
        for (Ingredient input : s.step.inputs) {
          Optional<Hash> inpHash;
          try {
            inpHash = input.hash();
            if (!inpHash.isPresent()) {
              log.warn(
                  "Step " + s.step.key + " has unsatisfied input " + input.key);
            }
          } catch (IOException ex) {
            log.warn("Could not hash " + input.key + " to " + s.step.key, ex);
          }
        }
      }
      throw new MojoExecutionException(
          "All remaining plan steps have unsatisfied inputs");
    }
    StepPlanState step = ready.remove(0);
    Preconditions.checkState(step.nUnsatisfiedInputs == 0);

    // We need to rebuild if any of the inputs' or hashes don't match.
    boolean hashesOk = true;
    List<Hash> inputHashes = Lists.newArrayList();
    for (Ingredient inp : step.step.inputs) {
      Optional<Hash> inpHash = Optional.absent();
      try {
        inpHash = inp.hash();
      } catch (IOException ex) {
        log.warn("Failed to hash " + inp.key + " for " + step.step.key, ex);
      }
      if (inpHash.isPresent()) {
        inputHashes.add(inpHash.get());
      } else {
        hashesOk = false;
        log.warn("Missing hash for " + inp.key);
      }
    }

    boolean outputsExist = true;

    Optional<Hash> storedStepHash = hashStore.getHash(step.step.key);
    Hash stepHash = Hash.hashAllHashes(inputHashes);

    boolean reuse = hashesOk
        && storedStepHash.isPresent()
        && stepHash.equals(storedStepHash.get())
        && outputsExist;

    // Maybe rebuild.
    if (reuse) {
      log.debug("Reusing output of " + step.step.key);
    } else {
      log.debug("Executing " + step.step.key);
      step.step.execute(log);
      log.debug("Executed " + step.step.key);
    }

    // Update dependency satisfaction counts and enqueue newly ready.
    for (Ingredient out : step.step.outputs) {
      for (StepPlanState dep : byInput.get(out.key)) {
        Preconditions.checkState(dep.nUnsatisfiedInputs > 0);
        --dep.nUnsatisfiedInputs;
        if (dep.nUnsatisfiedInputs == 0) {
          log.debug(dep.step.key + " is ready for execution");
          unexecuted.remove(dep);
          ready.add(dep);
        }
      }
    }

    hashStore.setHash(step.step.key, stepHash);
    addSteps(step.step.extraSteps(log));
  }
}