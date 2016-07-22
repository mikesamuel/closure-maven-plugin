package com.google.common.html.plugin;

import java.io.Serializable;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * Options for a compiler.
 */
@SuppressWarnings("serial")  // is abstract
public abstract class Options implements Cloneable, Serializable {

  /**
   * An ID that must be unique among a bundle of options of the same kind used
   * in a compilation batch.
   * <p>
   * May be null if this has not been disambiguated as per
   * {@link OptionsUtils#disambiguateIds}.
   */
  @Parameter
  public String id;

  /**
   * An ID that must be unique among a bundle of options of the same kind used
   * in a compilation batch.
   * <p>
   * May be null if this has not been disambiguated as per
   * {@link OptionsUtils#disambiguateIds}.
   */
  public final String getId() {
    return id;
  }

  /**
   * A key ingredient that must not overlap with options of a different kind.
   */
  public final String getKey() {
    String clName = getClass().getName();
    return id == null ? clName : clName + ":" + id;
  }

  /**
   * A best-effort copy since options have public immutable fields so that the
   * plexus configurator can muck with them.
   */
  @Override
  public Options clone() throws CloneNotSupportedException {
    return (Options) super.clone();
  }
}
