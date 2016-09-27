package com.google.closure.plugin.common;

import org.junit.Test;

import com.google.common.collect.ImmutableSet;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class FileExtTest extends TestCase {

  @Test
  public static void testReverse() {
    assertEquals(
        ImmutableSet.of("js", "ts"),
        FileExt.JS.allSuffixes());
    assertEquals(
        ImmutableSet.of("css", "gss"),
        FileExt.CSS.allSuffixes());
    assertEquals(
        ImmutableSet.of("proto"),
        FileExt.PROTO.allSuffixes());
    assertEquals(
        ImmutableSet.of("foo"),
        FileExt.valueOf("foo").allSuffixes());
  }

}
