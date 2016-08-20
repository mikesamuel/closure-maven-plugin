package com.google.closure.plugin.common;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.css.MinimalSubstitutionMap;
import com.google.common.css.OutputRenamingMapFormat;
import com.google.common.css.ReusableSubstitutionMap;
import com.google.common.css.SubstitutionMapProvider;
import com.google.common.io.CharSource;
import com.google.common.io.Files;

/**
 * A simple container for a minimal substitution map which tries to assign
 * small names.
 */
public final class StableCssSubstitutionMapProvider
implements SubstitutionMapProvider {

  /** A minimal map used to do substitutions. */
  private final ReusableSubstitutionMap substitutionMap;
  /** The file used to persist this substitution map. */
  private final File backingFile;

  /**
   * @param backingFile a file that need not exist, but if it does, contains
   *     the content of the renaming map as formatted by
   *     {@link OutputRenamingMapFormat#JSON}..
   */
  public StableCssSubstitutionMapProvider(File backingFile)
  throws IOException {
    CharSource renameMapJson = Files.asCharSource(backingFile, Charsets.UTF_8);
    ReusableSubstitutionMap newSubstitutionMap = null;
    try {
      try (Reader reader = renameMapJson.openBufferedStream()) {
        newSubstitutionMap = ReusableSubstitutionMap.read(
            OutputRenamingMapFormat.JSON,
            new MakeMinimalSubstMap(),
            ImmutableSet.<String>of(),
            reader);
      }
    } catch (@SuppressWarnings("unused") FileNotFoundException ex) {
      // Ok.  Fallthrough to below.
    }
    if (newSubstitutionMap == null) {
      newSubstitutionMap = new ReusableSubstitutionMap(new MakeMinimalSubstMap()
          .apply(ImmutableList.<String>of()));
    }
    this.substitutionMap = newSubstitutionMap;
    this.backingFile = backingFile;
  }

  @Override
  public ReusableSubstitutionMap get() {
    return substitutionMap;
  }

  /** The file used to persist this substitution map. */
  public File getBackingFile() {
    return this.backingFile;
  }
}


final class MakeMinimalSubstMap
implements Function<Iterable<? extends String>, MinimalSubstitutionMap> {

  @Override
  public MinimalSubstitutionMap apply(Iterable<? extends String> exclusions) {
    return new MinimalSubstitutionMap(ImmutableSet.copyOf(exclusions));
  }
}