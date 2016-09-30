package com.google.closure.plugin.common;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import org.codehaus.plexus.util.Scanner;

import com.google.closure.plugin.common.Sources.Source;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

/**
 * Describes a set of files.
 */
public final class DirectoryScannerSpec
implements Serializable, StructurallyComparable {
  private static final long serialVersionUID = 2622978832273403508L;

  /** The roots to scan. */
  public final ImmutableList<TypedFile> roots;

  /** <code>**<!---->/*.ext</code> style paths to include. */
  public final ImmutableList<String> includes;

  /** <code>**<!---->/*.ext</code> style paths to exclude. */
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

  private static final String[] EMPTY_STRING_ARRAY = new String[0];

  /** An empty scanner spec that is guaranteed to produce zero matched files. */
  public static final DirectoryScannerSpec EMPTY = new DirectoryScannerSpec(
      ImmutableList.<TypedFile>of(),
      ImmutableList.<String>of(),
      ImmutableList.<String>of());

  /**
   * Scans using the given plexus scanner.
   * This is like {@link Sources#scan} but uses a provided scanner like one of
   * the special purpose ones provided by
   * {@link org.sonatype.plexus.build.incremental.BuildContext}.
   */
  public ImmutableList<Source> scan(
      Scanner s, Iterable<SourceFileProperty> ps)
  throws IOException {
    s.setIncludes(includes.toArray(EMPTY_STRING_ARRAY));
    s.setExcludes(excludes.toArray(EMPTY_STRING_ARRAY));
    s.scan();
    String[] files = s.getIncludedFiles();
    if (files == null) { return ImmutableList.of(); }

    TypedFile root = new TypedFile(s.getBasedir().getCanonicalFile(), ps);
    String prefix = root.f.getPath();
    if (prefix.isEmpty()) { prefix = "."; }
    if (!prefix.endsWith(File.separator)) {
      prefix += File.separator;
    }

    ImmutableList.Builder<Source> sources = ImmutableList.builder();
    for (String file : files) {
      sources.add(new Source(
          new File(prefix + file).getCanonicalFile(), root, new File(file)));
    }
    return sources.build();
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