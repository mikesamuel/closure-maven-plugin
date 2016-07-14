package com.google.common.html.plugin;

import com.google.common.css.MinimalSubstitutionMap;
import com.google.common.css.SubstitutionMap;
import com.google.common.css.SubstitutionMapProvider;

/**
 * A simple container for a minimal substitution map which tries to assign
 * small names.
 */
public class ClosureMavenPluginSubstitutionMapProvider
implements SubstitutionMapProvider {

  private final SubstitutionMap substitutionMap = new MinimalSubstitutionMap();

  public SubstitutionMap get() {
    return substitutionMap;
  }
}
