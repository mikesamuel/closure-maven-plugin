package com.google.common.html.plugin.soy;

import java.io.File;

import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.html.plugin.Options;
import com.google.common.html.plugin.OptionsUtils;
import com.google.template.soy.SoyFileSet;

/**
 * An ID that must be unique among a bundle of options of the same kind used
 * in a compilation batch.
 */
public final class SoyOptions extends Options {

  private static final long serialVersionUID = -7199213790460881298L;

  /** Source root directories for {@code .soy} files. */
  public File[] source;

  /** whether to allow external calls (calls to undefined templates). */
  public Boolean allowExternalCalls;

  /** A JSON map of global names to values. */
  public String compileTimeGlobals;

  /**
   * true to force strict autoescaping. Enabling will cause compile time
   * exceptions if non-strict autoescaping is used in namespaces or templates.
   */
  public Boolean strictAutoescapingRequired;

  /** JS backend-specific options to the soy compiler. */
  public Js[] js;


  @Override
  public SoyOptions clone() throws CloneNotSupportedException {
    return (SoyOptions) super.clone();
  }


  /**
   * Creates a soy file set builder from this option sets fields.
   */
  public SoyFileSet.Builder toSoyFileSetBuilder(Log log) {
    SoyFileSet.Builder builder = SoyFileSet.builder();
    if (this.allowExternalCalls != null) {
      builder.setAllowExternalCalls(this.allowExternalCalls);
    }
    if (compileTimeGlobals != null) {
      Optional<ImmutableMap<String, Object>> globals =
          OptionsUtils.keyValueMapFromJson(log, compileTimeGlobals);
      if (globals.isPresent()) {
        builder.setCompileTimeGlobals(globals.get());
      }
    }
    if (strictAutoescapingRequired != null) {
      builder.setStrictAutoescapingRequired(strictAutoescapingRequired);
    }
    return builder;
  }
}
