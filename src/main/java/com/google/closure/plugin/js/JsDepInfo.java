package com.google.closure.plugin.js;

import java.io.File;
import java.io.Serializable;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.closure.plugin.js.Identifier.GoogNamespace;
import com.google.closure.plugin.plan.Metadata;
import com.google.javascript.jscomp.CompilerInput;

/**
 * Maps source file canonical paths to dependency info.
 */
public final class JsDepInfo implements Serializable {

  private static final long serialVersionUID = 1926393541882375428L;

  /**
   * Maps source file canonical paths to dependency info.
   */
  public final ImmutableMap<File, Metadata<DepInfo>> depinfo;

  JsDepInfo(Map<? extends File, ? extends Metadata<DepInfo>> depinfo) {
    this.depinfo = ImmutableMap.copyOf(depinfo);
  }

  /**
   * Information about which symbols a JS source file requires/provides.
   */
  public static final class DepInfo implements Serializable {
    private static final long serialVersionUID = 8112272591344220966L;

    /** Any module declaration present in the source file. */
    public final boolean isModule;
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

    DepInfo(
        boolean isModule,
        String closureCompilerInputName,
        Iterable<? extends GoogNamespace> provides,
        Iterable<? extends GoogNamespace> requires) {
      this.isModule = isModule;
      this.closureCompilerInputName = closureCompilerInputName;
      this.provides = ImmutableSet.copyOf(provides);
      this.requires = ImmutableSet.copyOf(requires);
    }

    @Override
    public String toString() {
      return "{DepInfo" + (isModule ? " module" : "")
          + (provides.isEmpty() ? "" : " provides(" + provides + ")")
          + (requires.isEmpty() ? "" : " requires(" + requires + ")")
          + "}";
    }
  }
}
