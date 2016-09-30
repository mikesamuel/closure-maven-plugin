package com.google.closure.plugin.common;

/**
 * Equals and hashCode based on recursive comparison of fields.
 */
public interface StructurallyComparable {
  @Override
  boolean equals(Object o);

  @Override
  int hashCode();
}
