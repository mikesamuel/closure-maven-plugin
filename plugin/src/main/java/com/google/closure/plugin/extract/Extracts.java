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

  @Override
  public Extracts clone() {
    Extracts clone = new Extracts();
    clone.id = id;
    for (Extract e : this.extract) {
      clone.extract.add(e.clone());
    }
    return clone;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((extract == null) ? 0 : extract.hashCode());
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
    Extracts other = (Extracts) obj;
    if (!super.equals(other)) {
      return false;
    }
    if (extract == null) {
      if (other.extract != null) {
        return false;
      }
    } else if (!extract.equals(other.extract)) {
      return false;
    }
    return true;
  }
}
