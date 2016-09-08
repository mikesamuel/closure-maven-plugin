package com.example.demo;

/**
 * A version of the demo that shows progressing from a borken state to
 * a fixed one.
 */
public enum DemoVariant {
  /**
   * Problematic ad-hoc HTML generation that contains an obvious XSS
   * vulnerability.
   */
  INSECURE,
  /**
   * A variant without the obvious vulnerability but which over-compensates
   * by over-escaping inputs that specify no executable code.
   */
  OVER_ESCAPING,
  /**
   * A variant that fixes the obvious vulnerability without over-escaping.
   */
  FIXED,
  ;


  /** Used by {@link Demo#main} when none specified. */
  public static final DemoVariant DEFAULT = FIXED;
}
