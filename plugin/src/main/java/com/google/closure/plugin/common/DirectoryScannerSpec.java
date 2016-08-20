package com.google.closure.plugin.common;

import java.io.Serializable;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

/**
 * Describes a set of files.
 */
public final class DirectoryScannerSpec implements Serializable {
  private static final long serialVersionUID = 2622978832273403508L;

  /** The roots to scan. */
  public final ImmutableList<TypedFile> roots;

  /** <code>**<nobr></nobr>/*.ext</code> style paths to include. */
  public final ImmutableList<String> includes;

  /** <code>**<nobr></nobr>/*.ext</code> style paths to exclude. */
  public final ImmutableList<String> excludes;

  /** */
  public DirectoryScannerSpec(
      Iterable<? extends TypedFile> roots,
      Iterable<? extends String> includes,
      Iterable<? extends String> excludes) {
    this.roots = ImmutableList.copyOf(roots);
    this.includes = ImmutableList.copyOf(includes);
    this.excludes = ImmutableList.copyOf(excludes);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DirectoryScannerSpec)) { return false; }
    DirectoryScannerSpec that = (DirectoryScannerSpec) o;
    return this.roots.equals(that.roots)
        && this.includes.equals(that.includes)
        && this.excludes.equals(that.excludes);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(roots, includes, excludes);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("{DirectoryScanner ");
    sb.append(roots);
    if (!includes.isEmpty()) {
      sb.append(" includes=").append(includes);
    }
    if (!excludes.isEmpty()) {
      sb.append(" excludes=").append(excludes);
    }
    return sb.append('}').toString();
  }
}