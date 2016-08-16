package com.google.common.html.plugin.soy;

import java.util.Map;

import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.html.plugin.common.Options;
import com.google.common.html.plugin.common.OptionsUtils;
import com.google.common.html.plugin.common.SourceOptions;
import com.google.template.soy.SoyFileSet;

/**
 * An ID that must be unique among a bundle of options of the same kind used
 * in a compilation batch.
 */
public final class SoyOptions extends SourceOptions {

  private static final long serialVersionUID = -7199213790460881298L;

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
  SoyFileSet.Builder toSoyFileSetBuilder(Log log) {
    SoyFileSet.Builder builder = SoyFileSet.builder();

    // external calls are incompatible with the jbcsrc backend and with the
    // spirit of strict mode.
    builder.setAllowExternalCalls(false);

    if (compileTimeGlobals != null) {
      Optional<ImmutableMap<String, Object>> globals =
          OptionsUtils.keyValueMapFromJson(log, compileTimeGlobals);
      if (globals.isPresent()) {
        Map<String, ?> globalsMap = globals.get();
        builder.setCompileTimeGlobals(globalsMap);
      }
    }

    if (strictAutoescapingRequired != null) {
      builder.setStrictAutoescapingRequired(
          strictAutoescapingRequired.booleanValue());
    }

    return builder;
  }


  @Override
  protected void createLazyDefaults() {
    if (this.js == null || js.length == 0) {
      this.js = new Js[] { new Js() };
    }
  }

  @Override
  protected ImmutableList<Js> getSubOptions() {
    return this.js != null
        ? ImmutableList.copyOf(this.js)
        : ImmutableList.<Js>of();
  }

  @Override
  protected void setSubOptions(ImmutableList<? extends Options> newOptions) {
    ImmutableList.Builder<Js> newJs = ImmutableList.builder();
    for (Options o : newOptions) {
      newJs.add((Js) o);
    }
    this.js = newJs.build().toArray(new Js[newOptions.size()]);
  }


  @Override
  protected ImmutableList<String> sourceExtensions() {
    return ImmutableList.of("soy");
  }
}
