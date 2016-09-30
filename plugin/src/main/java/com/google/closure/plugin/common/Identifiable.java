package com.google.closure.plugin.common;

/**
 * An instance that may be addressed by its ID within a group of similar
 * objects.
 */
public interface Identifiable {
  /** An identity which should be unique within a group. */
  public String getId();
}
