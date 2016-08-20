package com.google.closure.plugin.genjava;

import com.google.common.base.Preconditions;

final class JavaWriter {
  private final StringBuilder sb = new StringBuilder();
  private int indent = 0;
  private boolean atLineStart = true;

  JavaWriter appendCode(String code) {
    int written = 0, n = code.length();
    char quotes = 0;
    for (int i = 0; i < n; ++i) {
      char ch = code.charAt(i);
      if (atLineStart && ch != '\n') {
        int nToIndent = indent;
        if ((ch == '}' || ch == ')' || ch == ']')
            && nToIndent != 0) {
          --nToIndent;
        }
        Preconditions.checkState(written == i);
        for (; nToIndent != 0; --nToIndent) {
          sb.append("  ");
        }
        atLineStart = false;
      }
      switch (ch) {
        case '"': case '\'':
          if (quotes == 0) {
            quotes = ch;
          } else if (quotes == ch) {
            quotes = 0;
          }
          break;
        case '{': case '(': case '[':
          if (quotes == 0) {
            ++indent;
          }
          break;
        case '\\':
          ++i;
          break;
        case '}': case ')': case ']':
          if (quotes == 0) {
            Preconditions.checkState(indent != 0);
            --indent;
          }
          break;
        case '\n':
          atLineStart = true;
          Preconditions.checkState(0 == quotes);
          sb.append(code, written, i + 1);
          written = i + 1;
          break;
      }
    }
    sb.append(code, written, n);
    return this;
  }

  JavaWriter appendStringLiteral(String plainText) {
    sb.append('"');
    int written = 0;
    int n = plainText.length();
    for (int i = 0; i < n; ++i) {
      char repl;
      char ch = plainText.charAt(i);
      switch (ch) {
        case '\\': case '"':
          repl = ch;
          break;
        case '\0':
          repl = '0';
          break;
        case '\t':
          repl = 't';
          break;
        case '\f':
          repl = 'f';
          break;
        case '\n':
          repl = 'n';
          break;
        case '\r':
          repl = 'r';
          break;
        default:
          continue;
      }
      sb.append(plainText, written, i).append('\\').append(repl);
      written = i + 1;
    }
    sb.append(plainText, written, n);
    sb.append('"');
    return this;
  }

  JavaWriter nl() {
    return appendCode("\n");
  }

  JavaWriter appendCommentPart(String text) {
    return appendCode(text.replace("*/", "*\\u200c/"));
  }

  String toJava() {
    return sb.toString();
  }
}