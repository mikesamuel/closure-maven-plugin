package com.google.closure.plugin;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.css.MinimalSubstitutionMap;
import com.google.common.css.OutputRenamingMapFormat;
import com.google.common.css.ReusableSubstitutionMap;
import com.google.common.css.SubstitutionMapProvider;
import com.google.common.io.CharSource;

/**
 * A simple container for a minimal substitution map which tries to assign
 * small names.
 */
public class ClosureMavenPluginSubstitutionMapProvider
implements SubstitutionMapProvider {

  /** A minimal map used to do substitutions. */
  private final ReusableSubstitutionMap substitutionMap;

  /**
   * @param renameMapJson if present, the content of the renaming map as
   *     formatted by {@link OutputRenamingMapFormat#JSON}..
   */
  public ClosureMavenPluginSubstitutionMapProvider(CharSource renameMapJson)
  throws IOException {
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
  }

  @Override
  public ReusableSubstitutionMap get() {
    return substitutionMap;
  }
}


final class MakeMinimalSubstMap
implements Function<Iterable<? extends String>, MinimalSubstitutionMap> {

  @Override
  public MinimalSubstitutionMap apply(Iterable<? extends String> exclusions) {
    return new MinimalSubstitutionMap(ImmutableSet.copyOf(exclusions));
  }
}