package com.google.closure.plugin.common;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.css.MinimalSubstitutionMap;
import com.google.common.css.OutputRenamingMapFormat;
import com.google.common.css.RecordingSubstitutionMap;
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
  private final RecordingSubstitutionMap substitutionMap;
  /** The file used to persist this substitution map. */
  private final File backingFile;
  /** The original mappings loaded from the file. */
  private final ImmutableMap<String, String> originalMappings;

  /**
   * @param backingFile a file that need not exist, but if it does, contains
   *     the content of the renaming map as formatted by
   *     {@link OutputRenamingMapFormat#JSON}..
   */
  public StableCssSubstitutionMapProvider(File backingFile)
  throws IOException {
    CharSource renameMapJson = Files.asCharSource(backingFile, Charsets.UTF_8);
    RecordingSubstitutionMap.Builder substitutionMapBuilder =
        new RecordingSubstitutionMap.Builder()
        .withSubstitutionMap(
            new MinimalSubstitutionMap());
    ImmutableMap<String, String> mappings = ImmutableMap.of();
    try {
      try (Reader reader = renameMapJson.openBufferedStream()) {
        mappings = OutputRenamingMapFormat.JSON.readRenamingMap(reader);
      }
    } catch (@SuppressWarnings("unused") FileNotFoundException ex) {
      // Ok.  Start with an empty map.
    }

    substitutionMapBuilder.withMappings(mappings);

    this.substitutionMap = substitutionMapBuilder.build();
    this.backingFile = backingFile;
    this.originalMappings = mappings;
  }

  @Override
  public RecordingSubstitutionMap get() {
    return substitutionMap;
  }

  /** The file used to persist this substitution map. */
  public File getBackingFile() {
    return this.backingFile;
  }

  /** True if the mappings have changed. */
  public boolean hasChanged() {
    return !this.originalMappings.equals(substitutionMap.getMappings());
  }
}
