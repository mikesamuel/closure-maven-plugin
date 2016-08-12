package com.google.common.html.plugin.extract;

import org.junit.Test;

import com.google.common.base.Charsets;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class FindProtoPackageStmtTest extends TestCase {

  @Test
  public static void testExtractPackageFromContent() {
    FindProtoPackageStmt finder = new FindProtoPackageStmt();

    assertEquals(
        "foo/bar/baz.proto",
        finder.chooseRelativePath(
            "proto/foo/bar/baz.proto",

            "// Copy\nsyntax = proto2;\npackage foo.bar;\n"
            .getBytes(Charsets.UTF_8))
        );
    assertEquals(
        "proto/foo/bar/baz.proto",
        finder.chooseRelativePath(
            "proto/foo/bar/baz.proto",

            "// Copy\nsyntax = proto2;\n"
            .getBytes(Charsets.UTF_8))
        );
  }

}
