package com.google.common.html.plugin.css;

import java.io.Serializable;

import com.google.common.collect.ImmutableMap;

/**
 * A hashable bundle of options.
 */
public class CssOptionsById implements Serializable {
  private static final long serialVersionUID = 3457902261751102507L;

  /** Maps option ids to options. */
  public final ImmutableMap<String, CssOptions> optionsById;

  CssOptionsById(Iterable<? extends CssOptions> options) {
    ImmutableMap.Builder<String, CssOptions> optionsMap =
        ImmutableMap.builder();
    for (CssOptions o : options) {
      optionsMap.put(o.id, o);
    }
    this.optionsById = optionsMap.build();
  }
}
