package com.google.closure.plugin.soy;

import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.closure.plugin.common.FileExt;
import com.google.closure.plugin.common.Options;
import com.google.closure.plugin.common.OptionsUtils;
import com.google.closure.plugin.common.SourceOptions;
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

  /** Add JS backend-specific options to the soy compiler. */
  public void setJs(Js js) {
    this.js.add(js);
  }
  private final List<Js> js = Lists.newArrayList();

  /** JS backend-specific options to the soy compiler. */
  public ImmutableList<Js> getJs() {
    return ImmutableList.copyOf(js);
  }


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
    if (this.js.isEmpty()) {
      this.js.add(new Js());
    }
  }

  @Override
  protected ImmutableList<Js> getSubOptions() {
    return getJs();
  }

  @Override
  protected void setSubOptions(ImmutableList<? extends Options> newOptions) {
    this.js.clear();
    for (Options o : newOptions) {
      this.js.add((Js) o);
    }
  }


  @Override
  protected ImmutableList<FileExt> sourceExtensions() {
    return ImmutableList.of(FileExt.SOY);
  }


  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((compileTimeGlobals == null) ? 0 : compileTimeGlobals.hashCode());
    result = prime * result + ((js == null) ? 0 : js.hashCode());
    result = prime * result + ((strictAutoescapingRequired == null) ? 0 : strictAutoescapingRequired.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    SoyOptions other = (SoyOptions) obj;
    if (compileTimeGlobals == null) {
      if (other.compileTimeGlobals != null) {
        return false;
      }
    } else if (!compileTimeGlobals.equals(other.compileTimeGlobals)) {
      return false;
    }
    if (js == null) {
      if (other.js != null) {
        return false;
      }
    } else if (!js.equals(other.js)) {
      return false;
    }
    if (strictAutoescapingRequired == null) {
      if (other.strictAutoescapingRequired != null) {
        return false;
      }
    } else if (!strictAutoescapingRequired.equals(other.strictAutoescapingRequired)) {
      return false;
    }
    return true;
  }
}
