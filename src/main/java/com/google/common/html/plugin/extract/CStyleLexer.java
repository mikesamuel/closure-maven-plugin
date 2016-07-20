package com.google.common.html.plugin.extract;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;

/**
 * Lexes into C-style tokens.
 */
final class CStyleLexer implements Iterable<CStyleLexer.Token> {
  final String content;

  static final Pattern TOKEN = Pattern.compile(
      ""
      + "[\t\n\r ]+"
      + "|//[^\r\n]*"
      + "|/[*].*?(?:[*](?:/|\\z)|\\z)"
      + "|\"(?:[^\"\\\\]|\\\\.?)*(?:\"|\\z)"
      + "|\'(?:[^\'\\\\]|\\\\.?)*(?:\'|\\z)"
      + "|0[xX][0-9A-Fa-f]+"
      + "|[0][1-3][3-7]{0,2}"
      + "|(?:[0-9]+(?:\\.[0-9]*)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?"
      + "|.",
      Pattern.DOTALL
      );

  CStyleLexer(String content) {
    this.content = content;
  }

  @Override
  public Iterator<Token> iterator() {
    return new Iterator<Token>() {
      private int left;
      private int right;
      private TokenType type;

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean hasNext() {
        lex();
        return type != null;
      }

      @Override
      public Token next() {
        lex();
        if (type == null) {
          throw new NoSuchElementException();
        }
        Token t = new Token(type, left, right);
        left = right;
        right = -1;
        type = null;
        return t;
      }

      private void lex() {
        int n = content.length();
        while (type == null && left < n) {
          Matcher m = TOKEN.matcher(content);
          if (m.find(left)) {
            right = m.end();
          } else {
            // Do orphaned-surrogates cause this?
            throw new AssertionError("Index " + left + " in " + content);
          }
          int cp = content.codePointAt(left);
          switch (cp) {
            case '\t': case '\n': case '\r': case ' ':
              break;
            case '/':
              type = right - left == 1
                  ? TokenType.PUNCTUATION : null;
              break;
            case '"': case '\'':
              type = TokenType.STRING;
              break;
            case '.':
              type = right - left == 1
                  ? TokenType.PUNCTUATION : TokenType.NUMBER;
              break;
            case '0': case '1': case '2': case '3': case '4':
            case '5': case '6': case '7': case '8': case '9':
              type = TokenType.NUMBER;
              break;
            default:
              if (Character.isUnicodeIdentifierStart(cp) || cp == '$') {
                type = TokenType.WORD;
                right = left;
                do {
                  right += Character.charCount(cp);
                } while (
                    right < n
                    && ((cp = content.codePointAt(right)) == '$'
                        || Character.isUnicodeIdentifierPart(cp)));
              } else {
                type = TokenType.PUNCTUATION;
              }
              break;
          }
          if (type == null) {
            Preconditions.checkState(right > left);
            left = right;
          }
        }
      }
    };
  }


  final class Token {
    final TokenType type;
    final int left;
    final int right;

    Token(TokenType type, int left, int right) {
      this.type = type;
      this.left = left;
      this.right = right;
    }

    boolean hasText(String s) {
      int n = s.length();
      return right - left == n && content.regionMatches(left, s, 0, n);
    }

    @Override
    public String toString() {
      return content.substring(left, right);
    }
  }

  enum TokenType {
    WORD,
    NUMBER,
    PUNCTUATION,
    STRING,
    ;
  }

  private static long SPACE_CHARS =
      (1L << '\t') | (1L << '\n') | (1L << '\r') | (1L << ' ');

  static boolean isSpace(int cp) {
    return cp <= 32 && (SPACE_CHARS & (1 << cp)) != 0;
  }

  static boolean isWordStart(int cp) {
    return Character.isJavaIdentifierStart(cp);
  }
}
