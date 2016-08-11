package com.google.common.html.plugin.js;

import java.io.File;
import java.io.Serializable;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.html.plugin.js.Identifier.GoogNamespace;
import com.google.common.html.plugin.plan.Hash;
import com.google.javascript.jscomp.CompilerInput;

/**
 * Maps source file canonical paths to dependency info.
 */
public final class JsDepInfo implements Serializable {

  private static final long serialVersionUID = 1926393541882375428L;

  /**
   * Maps source file canonical paths to dependency info.
   */
  public final ImmutableMap<File, HashAndDepInfo> depinfo;

  JsDepInfo(Map<? extends File, ? extends HashAndDepInfo> depinfo) {
    this.depinfo = ImmutableMap.copyOf(depinfo);
  }

  /**
   * Information about which symbols a JS source file requires/provides.
   */
  public static final class HashAndDepInfo implements Serializable {
    private static final long serialVersionUID = 8112272591344220966L;

    /**
     * Hash of the contents of the JS file from which provides/requires
     * were parsed.
     */
    public final Hash hash;
    /**
     * Statically findable arguments to {@code goog.provide}.
     */
    public final ImmutableSet<GoogNamespace> provides;
    /**
     * Statically findable arguments to {@code goog.require}.
     */
    public final ImmutableSet<GoogNamespace> requires;
    /**
     * The input name as determined by {@link CompilerInput#getName()}.
     */
    public final String closureCompilerInputName;

    HashAndDepInfo(
        Hash hash,
        String closureCompilerInputName,
        Iterable<? extends GoogNamespace> provides,
        Iterable<? extends GoogNamespace> requires) {
      this.hash = hash;
      this.closureCompilerInputName = closureCompilerInputName;
      this.provides = ImmutableSet.copyOf(provides);
      this.requires = ImmutableSet.copyOf(requires);
    }
  }
}
