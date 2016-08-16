package com.google.closure.plugin.common;

import org.apache.maven.plugin.logging.Log;

import com.google.closure.plugin.common.Ingredients
     .SettableFileSetIngredient;

/**
 * Finds an executable file that can be used to compile source files.
 */
public interface ToolFinder<OPTIONS> {
  /**
   * @param options options that may override the default search path.
   * @param ingredients used to construct file ingredients.
   * @param toolPathOut receives the tool files as main sources.
   */
  void find(
      Log log, OPTIONS options, Ingredients ingredients,
      SettableFileSetIngredient toolPathOut);
}
