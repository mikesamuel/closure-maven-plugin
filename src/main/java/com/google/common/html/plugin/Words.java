package com.google.common.html.plugin;

import com.google.common.base.Ascii;

public final class Words {

  private Words() {
  }

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
