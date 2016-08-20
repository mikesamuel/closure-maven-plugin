package com.google.closure.plugin.common;

import com.google.common.base.Ascii;

/**
 * Utilities for checking file naming conventions.
 */
public final class Words {

  private Words() {
  }

  /**
   * True when s ends with suffix in a way that suffix is not part of a larger
   * word by camel-casing or underscore naming conventions.
   */
  public static boolean endsWithWordOrIs(String s, String suffix) {
    if (suffix.isEmpty()) {
      return true;
    }
    String lc = Ascii.toLowerCase(s);
    if (lc.endsWith(suffix)) {
      int beforeSuffix = lc.length() - suffix.length() - 1;
      if (beforeSuffix < 0) {
        return true;
      }
      if (!Character.isLetterOrDigit(lc.charAt(beforeSuffix))) {
        return true;
      }
      if (Ascii.isLowerCase(s.charAt(beforeSuffix))
          && Ascii.isUpperCase(s.charAt(beforeSuffix + 1))) {
        return true;
      }
    }
    return false;
  }
}
