package com.google.common.html.plugin.common;

import java.io.File;
import java.io.Serializable;
import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/** A file with property information. */
public final class TypedFile implements Serializable {
  private static final long serialVersionUID = -9096009178555037340L;

  /** The file path. */
  public final File f;
  /** Properties associated with {@link #f}. */
  public final ImmutableSet<SourceFileProperty> ps;

  /** */
  public TypedFile(File f, Iterable<SourceFileProperty> ps) {
    this.f = Preconditions.checkNotNull(f);
    this.ps = Sets.immutableEnumSet(ps);
  }

  /** */
  public TypedFile(File f, SourceFileProperty... ps) {
    this(f, Arrays.asList(ps));
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof TypedFile)) { return false; }
    TypedFile that = (TypedFile) o;
    return this.f.equals(that.f) && this.ps.equals(that.ps);
  }

  @Override
  public int hashCode() {
    return f.hashCode() + 31 * ps.hashCode();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("{TypedFile ").append(f);
    for (SourceFileProperty p : ps) {
      sb.append(' ').append(p);
    }
    return sb.append('}').toString();
  }
}
