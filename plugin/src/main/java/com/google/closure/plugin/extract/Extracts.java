package com.google.closure.plugin.extract;

import java.util.List;

import com.google.closure.plugin.common.Options;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Specify how to extract JS, CSS, Proto, and Soy dependencies from Maven
 * artifacts.
 */
public final class Extracts extends Options {

  private static final long serialVersionUID = 6857217093408402466L;

  private final List<Extract> extract = Lists.newArrayList();
  /** Add artifact from which to extract dependencies or source files. */
  public void setExtract(Extract e) {
    this.extract.add(e);
  }

  /** Artifacts from which to extract dependencies or source files. */
  public ImmutableList<Extract> getExtracts() {
    return ImmutableList.copyOf(extract);
  }

  @Override
  protected void createLazyDefaults() {
    // Done
  }
}
