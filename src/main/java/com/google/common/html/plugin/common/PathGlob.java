package com.google.common.html.plugin.common;

import java.io.Serializable;
import java.util.regex.Pattern;

import com.google.common.base.Predicate;

/**
 * A glob that can be plexus-configured.
 */
public final class PathGlob
implements Predicate<CharSequence>, Serializable, Cloneable {
  private static final long serialVersionUID = 3378502926949748347L;

  private static final Pattern MATCHES_NONE = Pattern.compile("\\z.");

  private String globString;
  private Pattern pattern;

  /** Constructs an uninitialized path glob. */
  public PathGlob() {
    this("<uninitialized>", MATCHES_NONE);
  }

  /** */
  public PathGlob(String globString) {
    this(globString, globStringToPattern(globString));
  }

  private PathGlob(String globString, Pattern pattern) {
    this.globString = globString;
    this.pattern = pattern;
  }

  /** Plexus-configurator compatible default setter. */
  @Deprecated  // Warn if used directly instead of by plexus configurator.
  public void set(String glob) {
    this.pattern = globStringToPattern(glob);
    this.globString = glob;
  }

  private static final String SIMPLE_FILE_SEP_PATTERN = "[/\\\\]";
  private static final String AFTER_FILE_SEP_PATTERN =
      "(?<=" + SIMPLE_FILE_SEP_PATTERN + ")";
  private static final String BEFORE_FILE_SEP_PATTERN =
      "(?=" + SIMPLE_FILE_SEP_PATTERN + ")";
  private static final String END_OF_INPUT = "\\z";

  /**
   * A Unix/Windows path separator or end of input since the separator is
   * redundant at the end of a path, or adjacent to a path separator as in
   * "\\**\\" matching "\\"."
   */
  private static final String FILE_SEP_PATTERN =
      "(?:" + SIMPLE_FILE_SEP_PATTERN
      + "|" + END_OF_INPUT
      + "|" + AFTER_FILE_SEP_PATTERN + ")";

  private static final String DOUBLE_STAR_PATTERN =
      "(?:"
      + ".*(?:" + BEFORE_FILE_SEP_PATTERN + "|" + END_OF_INPUT + ")"
      + "|" + AFTER_FILE_SEP_PATTERN + ")";

  static Pattern globStringToPattern(String glob) {
    int limit = glob.length();
    int included = 0;
    StringBuilder sb = new StringBuilder();
    sb.append("^(?:");
    for (int i = 0; i < limit; ++i) {
      char ch = glob.charAt(i);
      String replacement = null;
      int end = i + 1;
      switch (ch) {
        case '/': case '\\':
          replacement = FILE_SEP_PATTERN;
          break;
        case '*':
          if (end < limit && glob.charAt(end) == '*') {
            ++end;
            replacement = DOUBLE_STAR_PATTERN;
          } else {
            replacement = "[^/\\\\]*";
          }
          break;
      }
      if (replacement != null) {
        if (included != i) {
          sb.append(Pattern.quote(glob.substring(included, i)));
        }
        sb.append(replacement);
        included = end;
        i = end - 1;
      }
    }
    if (included != limit) {
      sb.append(Pattern.quote(glob.substring(included, limit)));
    }
    sb.append(")\\z");
    return Pattern.compile(sb.toString(), Pattern.DOTALL);
  }

  @Override
  public boolean apply(CharSequence path) {
    return pattern.matcher(path).matches();
  }

  @Override
  public String toString() {
    return globString;
  }

  /** The glob. */
  public String getGlobString() {
    return globString;
  }

  Pattern getPattern() {
    return pattern;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof PathGlob)) {
      return false;
    }
    PathGlob that = (PathGlob) o;
    return this.pattern.equals(that.pattern);
  }

  @Override
  public int hashCode() {
    return pattern.hashCode();
  }

  @Override
  public PathGlob clone() {
    return new PathGlob(globString, pattern);
  }
}
