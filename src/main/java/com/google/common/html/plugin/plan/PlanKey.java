package com.google.common.html.plugin.plan;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CompileTimeConstant;

/** A string wrapper for a key that refers to part of a plan. */
public final class PlanKey {
  /** Key text. */
  public final String text;

  PlanKey(String text) {
    this.text = Preconditions.checkNotNull(text);
  }

  @Override
  public int hashCode() { return text.hashCode(); }

  @Override
  public boolean equals(Object o) {
    return o instanceof PlanKey && this.text.equals(((PlanKey) o).text);
  }

  @Override
  public String toString() {
    return text;
  }

  /** A new factory for keys. */
  public static Builder builder(@CompileTimeConstant String prefix) {
    return new Builder(prefix);
  }

  /** Factory for keys. */
  public static final class Builder {
    private final StringBuilder sb = new StringBuilder();

    Builder(@CompileTimeConstant String prefix) {
      sb.append(Preconditions.checkNotNull(prefix)).append(':');
    }

    /** Includes the given ingredients in the key as a block. */
    public Builder addInp(Ingredient... inps) {
      return addInp(Arrays.asList(inps));
    }

    /** Includes the given ingredients in the key as a block. */
    public Builder addInp(Iterable<? extends Ingredient> inps) {
      char sep = '[';
      for (Ingredient inp : inps) {
        sb.append(sep);
        sep = ',';
        sb.append(inp.key);
      }
      sb.append(sep == '[' ? "[];" : "];");
      return this;
    }

    /** Includes the given strings in the key as a block. */
    public Builder addStrings(Iterable<? extends String> strs) {
      char sep = '[';
      for (String s : strs) {
        sb.append(sep);
        sep = ',';
        Escaper.DEFAULT.escape(s, sb);
      }
      sb.append(sep == '[' ? "[];" : "];");
      return this;
    }

    /** Escapes and appends the given free-form text. */
    public Builder addString(String text) {
      Escaper.DEFAULT.escape(text, sb);
      sb.append(';');
      return this;
    }

    /** The key appended thus far. */
    public PlanKey build() {
      return new PlanKey(sb.substring(0, sb.length() - 1));
    }
  }
}


/** Escapes keys for inclusion into other keys. */
final class Escaper {
  final Pattern metachar;

  static final Escaper DEFAULT = new Escaper();

  /** Defaults meta-characters that are significant in keys. */
  public Escaper() {
    this(':', '\\', '[', ']', ',', ';', '"');
  }

  Escaper(char... metachars) {
    StringBuilder sb = new StringBuilder();
    sb.append("[\\\\");
    for (char c : metachars) {
      switch (c) {
        case '-': case '\\': case '[': case ']': case '^':
          sb.append('\\');
          break;
      }
      sb.append(c);
    }
    sb.append(']');
    metachar = Pattern.compile(sb.toString());
  }

  StringBuilder escape(CharSequence s, StringBuilder out) {
    Matcher m = metachar.matcher(s);
    int written = 0;
    int n = s.length();
    while (m.find()) {
      int start = m.start();
      int end = m.end();
      out.append(s, written, start).append('\\').append(m.group());
      written = end;
    }
    out.append(s, written, n);
    return out;
  }

  StringBuilder escapeList(
      Iterable<? extends String> strs, StringBuilder out) {
    out.append('[');
    boolean sawOne = false;
    for (String s : strs) {
      if (sawOne) {
        out.append(',');
      } else {
        sawOne = true;
      }
      escape(s, out);
    }
    out.append(']');
    return out;
  }
}
