package com.google.closure.plugin.plan;

import java.io.Serializable;

import com.google.closure.plugin.common.StructurallyComparable;
import com.google.common.collect.ImmutableList;

/**
 * Partitions items based on the kind of processing they need.
 */
public final class Update<T extends Serializable>
implements Serializable, StructurallyComparable {
  private static final long serialVersionUID = 1L;

  /** Items that do not need to be reprocessed. */
  public final ImmutableList<T> unchanged;
  /** Items that need processing. */
  public final ImmutableList<T> changed;
  /** Items that might have been processed before but which should no longer
   * correspond to outputs.
   */
  public final ImmutableList<T> defunct;

  /** */
  public Update(
      Iterable<? extends T> unchanged,
      Iterable<? extends T> changed,
      Iterable<? extends T> defunct) {
    this.unchanged = ImmutableList.copyOf(unchanged);
    this.changed = ImmutableList.copyOf(changed);
    this.defunct = ImmutableList.copyOf(defunct);
  }

  /** The changed and unchanged items, but not any defunct. */
  public ImmutableList<T> allExtant() {
    return ImmutableList.<T>builder().addAll(unchanged).addAll(changed).build();
  }

  /** All items. */
  public ImmutableList<T> all() {
    return ImmutableList.<T>builder()
        .addAll(unchanged)
        .addAll(changed)
        .addAll(defunct)
        .build();
  }

  /** All changed items. */
  public ImmutableList<T> allChanged() {
    return ImmutableList.<T>builder()
        .addAll(changed)
        .addAll(defunct)
        .build();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((changed == null) ? 0 : changed.hashCode());
    result = prime * result + ((defunct == null) ? 0 : defunct.hashCode());
    result = prime * result + ((unchanged == null) ? 0 : unchanged.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    Update<?> other = (Update<?>) obj;
    if (changed == null) {
      if (other.changed != null) {
        return false;
      }
    } else if (!changed.equals(other.changed)) {
      return false;
    }
    if (defunct == null) {
      if (other.defunct != null) {
        return false;
      }
    } else if (!defunct.equals(other.defunct)) {
      return false;
    }
    if (unchanged == null) {
      if (other.unchanged != null) {
        return false;
      }
    } else if (!unchanged.equals(other.unchanged)) {
      return false;
    }
    return true;
  }

  /** True if this update includes changed files. */
  public boolean hasChanges() {
    return !(changed.isEmpty() && defunct.isEmpty());
  }
}
