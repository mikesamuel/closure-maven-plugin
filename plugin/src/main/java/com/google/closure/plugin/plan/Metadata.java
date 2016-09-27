package com.google.closure.plugin.plan;

import java.io.Serializable;

import com.google.common.base.Preconditions;

/**
 * Bundles a hash of a file's content and metadata extracted from that file.
 */
public final class Metadata<T extends Serializable>
implements Serializable, StructurallyComparable {
  private static final long serialVersionUID = 8537324866556404094L;

  /** Hash of the file from which the metadata was derived. */
  public final Hash hash;
  /** The extracted metadata. */
  public final T metadata;

  /** */
  public Metadata(Hash hash, T metadata) {
    this.hash = Preconditions.checkNotNull(hash);
    this.metadata = metadata;
  }

  @Override
  public String toString() {
    return metadata.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((hash == null) ? 0 : hash.hashCode());
    result = prime * result + ((metadata == null) ? 0 : metadata.hashCode());
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
    Metadata<?> other = (Metadata<?>) obj;
    if (hash == null) {
      if (other.hash != null) {
        return false;
      }
    } else if (!hash.equals(other.hash)) {
      return false;
    }
    if (metadata == null) {
      if (other.metadata != null) {
        return false;
      }
    } else if (!metadata.equals(other.metadata)) {
      return false;
    }
    return true;
  }
}