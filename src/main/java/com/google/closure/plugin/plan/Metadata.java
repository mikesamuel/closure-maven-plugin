package com.google.common.html.plugin.plan;

import java.io.Serializable;

import com.google.common.base.Preconditions;

/**
 * Bundles a hash of a file's content and metadata extracted from that file.
 */
public final class Metadata<T extends Serializable> implements Serializable {
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
}