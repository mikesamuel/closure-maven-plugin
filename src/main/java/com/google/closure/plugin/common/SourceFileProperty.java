package com.google.closure.plugin.common;

/**
 * A property of a file.
 */
public enum SourceFileProperty {
  /**
   * Not a core source file, but can be loaded to satisfy a dependency
   * of a core source file.
   */
  LOAD_AS_NEEDED,
  /**
   * Only used for testing, not as part of production code.
   */
  TEST_ONLY,
}
