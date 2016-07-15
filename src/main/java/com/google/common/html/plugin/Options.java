package com.google.common.html.plugin;

import java.io.Serializable;

/**
 * Options for a compiler.
 */
public interface Options extends Cloneable, Serializable {

  /**
   * An ID that must be unique among a bundle of options of the same kind used
   * in a compilation batch.
   */
  String getId();

  /**
   * A key ingredient that must not overlap with options of a different kind.
   */
  String getKey();

  /**
   * A best-effort copy since options have public immutable fields so that the
   * plexus configurator can muck with them.
   */
  Options clone() throws CloneNotSupportedException;
}
