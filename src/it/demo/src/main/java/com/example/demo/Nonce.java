package com.example.demo;

import java.security.SecureRandom;
import java.util.BitSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.io.BaseEncoding;

/** An unpredictable & meaningless string. */
final class Nonce {
  final String text;

  Nonce(String text) throws IllegalArgumentException {
    Preconditions.checkArgument(isValidNonceText(text), text);
    this.text = text;
  }

  static Nonce create(SecureRandom rnd) {
    byte[] bytes = new byte[32];
    rnd.nextBytes(bytes);
    return new Nonce(BaseEncoding.base64Url().encode(bytes));
  }

  @Override
  public int hashCode() {
    return text.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Nonce)) { return false; }
    Nonce that = (Nonce) o;
    int n0 = this.text.length();
    int n1 = that.text.length();
    boolean ok = n0 == n1;
    for (int i = 0, n = Math.min(n0, n1); i < n; ++i) {
      if (this.text.charAt(i) != that.text.charAt(i)) {
        ok = false;
      }
    }
    return ok;
  }


  private static final BitSet WEB_SAFE_CHARS = new BitSet();
  static {
    // Nonces should be URL-safe: https://tools.ietf.org/html/rfc4648#page-8
    for (char c = 'A'; c <= 'Z'; c += 1) { WEB_SAFE_CHARS.set(c); }
    for (char c = 'a'; c <= 'z'; c += 1) { WEB_SAFE_CHARS.set(c); }
    for (char c = '0'; c <= '9'; c += 1) { WEB_SAFE_CHARS.set(c); }
    WEB_SAFE_CHARS.set('-');
    WEB_SAFE_CHARS.set('_');
    WEB_SAFE_CHARS.set('=');
  }

  static boolean isValidNonceText(String text) {
    return text.length() >= 16 && isWebsafeUrlFragment(text);
  }

  @VisibleForTesting
  static boolean isWebsafeUrlFragment(String s) {
    for (int i = 0, n = s.length(); i < n; ++i) {
      if (!WEB_SAFE_CHARS.get(s.charAt(i))) {
        return false;
      }
    }
    return true;
  }
}