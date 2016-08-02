package com.google.common.html.plugin.common;

import org.junit.Test;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public class PathGlobTest extends TestCase {

  @Test
  public static void testGlob() {
    PathGlob glob = new PathGlob("foo/**/*.txt");

    assertFalse(glob.apply("example/foo.txt"));
    assertFalse(glob.apply("example/foo/bar.txt"));
    assertTrue(glob.apply("foo/baz/bar.txt"));
    assertFalse(glob.apply("foo/baz/bar.txt/boo"));
    assertFalse(glob.apply("foo/baz/bar.png"));
    assertFalse(glob.apply("Foo/baz/bar.txt"));
    assertFalse(glob.apply("/foo/baz/bar.txt"));
    // "/**/" should match "/"
    assertTrue(glob.apply("foo/bar.txt"));
  }

}
