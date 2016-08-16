package com.google.common.html.plugin.plan;

import java.io.Serializable;

/**
 * A serializable can be hashed, and the key can be used to persist
 * derived information along with the hash allowing the hash to be used to
 * test the freshness of the derived information.
 */
public interface KeyedSerializable extends Serializable {
  /**
   * A key that is unique among a set of items that might appear in a hash
   * store together.
   */
  PlanKey getKey();
}
