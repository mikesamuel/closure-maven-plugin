package com.google.closure.plugin.common;

import java.io.File;
import java.io.Serializable;
import java.util.Locale;

import org.apache.commons.io.IOCase;

import com.google.closure.plugin.common.Sources.Source;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Multimaps;

/**
 * A file extension as a proxy for file type.
 */
public final class FileExt implements Comparable<FileExt>, Serializable {
  private static final long serialVersionUID = 1L;

  /** The extension without the "." */
  public final String extension;

  /** {@code *} extension which means any extension in some contexts. */
  public static final FileExt _ANY = new FileExt("*");

  /** File extension constant. */
  public static final FileExt CLASS = new FileExt("class");
  /** File extension constant. */
  public static final FileExt CSS = new FileExt("css");
  /** File extension constant. */
  public static final FileExt JAVA = new FileExt("java");
  /** File extension constant. */
  public static final FileExt JS = new FileExt("js");
  /** File extension constant. */
  public static final FileExt JSON = new FileExt("json");
  /** File extension constant. */
  public static final FileExt PD = new FileExt("pd");
  /** File extension constant. */
  public static final FileExt PROTO = new FileExt("proto");
  /** File extension constant. */
  public static final FileExt SOY = new FileExt("soy");

  private static final ImmutableMap<String, String> CANONICAL_EXT =
      ImmutableMap.of(
          // Closure stylesheets compiler compiles CSS+stuff to CSS and some
          // use the gss extension for the input language though the compiler
          // does not require it.
          "gss", "css",
          // Typescript is just JS plus some stuff.  All of them go to
          // closure compiler.
          "ts", "js");

  private FileExt(String extension) {
    Preconditions.checkArgument(
        extension.indexOf('.') < 0 && !extension.isEmpty());
    this.extension = extension;
  }

  /** The file */
  public static FileExt valueOf(String ext) {
    String canon = CANONICAL_EXT.get(ext);
    if (canon == null) {
      canon = ext;
    }
    return new FileExt(canon);
  }

  /** The extension for the given file. */
  public static Optional<FileExt> forFile(String path) {
    int n = path.length();
    int dot = path.lastIndexOf('.');
    if (dot < 0 || dot + 1 == n) { return Optional.absent(); }
    for (int i = dot + 1; i < n; ++i) {
      char ch = path.charAt(i);
      if (ch == '/' || ch == '\\') {
        return Optional.absent();
      }
    }
    String ext = path.substring(dot + 1);
    if (IOCase.SYSTEM.isCaseSensitive()) {
      // TODO: This may not do the right thing on Mac.
      // TODO: Should this be done in valueOf?
      ext = ext.toLowerCase(Locale.ENGLISH);  // No locale-specific folding
    }
    return Optional.of(valueOf(ext));
  }

  /** The extension for the given file. */
  public static Optional<FileExt> forFile(File f) {
    return forFile(f.getName());
  }

  /** The extension for the given file. */
  public static Optional<FileExt> forFile(Source s) {
    return forFile(s.relativePath);
  }

  @Override
  public String toString() {
    return extension;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof FileExt)) { return false; }
    FileExt that = (FileExt) o;
    return this.extension.equals(that.extension);
  }

  @Override
  public int hashCode() {
    return extension.hashCode();
  }

  @Override
  public int compareTo(FileExt that) {
    return this.extension.compareTo(that.extension);
  }

  private static final ImmutableMultimap<String, String> NON_CANON =
      ImmutableMultimap.copyOf(Multimaps.invertFrom(
          Multimaps.forMap(CANONICAL_EXT),
          ArrayListMultimap.<String,String>create()));

  /**
   * All case-normalized suffixes <i>s</i> such that {@code this == valueOf(s)}.
   */
  public ImmutableSortedSet<String> allSuffixes() {
    return ImmutableSortedSet.<String>naturalOrder()
        .add(extension)
        .addAll(NON_CANON.get(extension))
        .build();
  }
}
