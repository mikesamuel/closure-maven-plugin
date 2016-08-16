package com.google.closure.plugin.plan;

/**
 * An ingredient that a {@link Step} can combine into an output.
 */
public abstract class Ingredient implements Hashable {
  /** A human readable label that uniquely identifies the ingredient. */
  public final PlanKey key;

  protected Ingredient(PlanKey key) {
    this.key = key;
  }

  @Override
  public String toString() {
    return key.text;
  }
}