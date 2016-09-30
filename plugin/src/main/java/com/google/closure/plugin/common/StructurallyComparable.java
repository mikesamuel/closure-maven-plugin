package com.google.closure.plugin.plan;

/**
 * Equals and hashCode based on recursive comparison of fields.
 */
public interface StructurallyComparable {
  @Override
  boolean equals(Object o);

  @Override
  int hashCode();
}
