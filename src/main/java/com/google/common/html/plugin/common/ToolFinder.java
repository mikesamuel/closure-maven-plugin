package com.google.common.html.plugin.common;

import com.google.common.html.plugin.common.Ingredients.SettableFileSetIngredient;

/**
 * Finds an executable file that can be used to compile source files.
 */
public interface ToolFinder<OPTIONS extends Options> {
  /**
   * @param options options that may override the default search path.
   * @param ingredients used to construct file ingredients.
   * @param toolPathOut receives the tool files as main sources.
   */
  void find(
      OPTIONS options, Ingredients ingredients,
      SettableFileSetIngredient toolPathOut);
}
