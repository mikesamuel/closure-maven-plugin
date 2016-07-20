package com.google.common.html.plugin.extract;

import java.io.Serializable;

import com.google.common.collect.ImmutableList;

/**
 * A list of {@link Extract}s that can be hashed.
 */
public final class ExtractsList implements Serializable {

  private static final long serialVersionUID = -8174082873145678783L;

  /** Extracts from the plugin configuration. */
  public final ImmutableList<Extract> extracts;

  ExtractsList(Iterable<? extends Extract> extracts) {
    this.extracts = ImmutableList.copyOf(extracts);
  }
}
