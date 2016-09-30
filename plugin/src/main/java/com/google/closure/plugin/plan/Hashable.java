package com.google.closure.plugin.plan;

/** Can be hashed. */
public interface Hashable {

  /**
   * A hash of this ingredients state.
   *
   * @return may be absent if this represents a resource like a file which has
   *     not yet been created.
   */
  Hash hash();
}
