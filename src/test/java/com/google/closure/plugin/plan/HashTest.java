package com.google.closure.plugin.plan;

import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public class HashTest extends TestCase {

  @Test
  public static void testToStringAndDecodeCtor() {
    Random rnd = new Random();
    for (int i = 0; i < 1000; ++i) {
      byte[] randomBytes = new byte[1024];
      rnd.nextBytes(randomBytes);

      Hash hash = new Hash(randomBytes);
      String encoded = hash.toString();
      Hash decoded = new Hash(encoded);

      String msg = encoded + " != " + decoded;

      assertEquals(msg, hash, decoded);

      assertTrue(msg,
          Arrays.equals(hash.getBytes(), decoded.getBytes()));
    }
  }

  @Test
  public static void testEquals() {
    Hash[] distinct = new Hash[] {
        new Hash(new byte[] { 33 }),
        new Hash(new byte[] { 0 }),
        new Hash(new byte[] { 127 }),
        new Hash(new byte[] { -1 }),
        new Hash(new byte[] { 0, 0 }),
        new Hash(new byte[] { 127, 0 }),
    };

    for (int i = 0; i < distinct.length; ++i) {
      Hash hi = distinct[i];
      for (int j = 0; j < distinct.length; ++j) {
        Hash hj = distinct[j];
        assertEquals(hi + "@" + i + " cmp " + hj + "@" + j,
            i == j, hi.equals(hj));
      }
    }

    Hash h = new Hash(new byte[] { 33 });
    assertEquals(distinct[0], h);
  }
}
