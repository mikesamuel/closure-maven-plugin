package com.google.common.html.plugin.plan;

/**
 * An ingredient that a {@link Step} can combine into an output.
 */
public abstract class Ingredient implements Hashable {
  /** A human readable label that uniquely identifies the ingredient. */
  public final String key;

  protected Ingredient(String key) {
    this.key = key;
  }

  @Override
  public String toString() {
    return key;
  }
}