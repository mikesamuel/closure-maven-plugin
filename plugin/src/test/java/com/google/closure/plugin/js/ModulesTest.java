package com.google.closure.plugin.js;

import java.io.File;

import com.google.common.base.Joiner;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class ModulesTest extends TestCase {

  public static void testRelativeToBestEffort() {
    assertEquals(
        f("foo", "bar.txt"),
        Modules.relativeToBestEffort(
            f("baz"), f("baz", "foo", "bar.txt")));
    assertEquals(
        null,
        Modules.relativeToBestEffort(
            f("baz"), f("boo", "foo", "bar.txt")));
    assertEquals(
        null,
        Modules.relativeToBestEffort(
            f("baz"), f("baz.txt")));
  }

  private static File f(String... parts) {
    return new File(Joiner.on(File.separatorChar).join(parts));
  }
}
