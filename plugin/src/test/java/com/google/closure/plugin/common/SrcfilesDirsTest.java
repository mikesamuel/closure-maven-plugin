package com.google.closure.plugin.common;

import java.io.File;

import org.junit.Test;

import com.google.closure.plugin.common.FileExt;
import com.google.common.collect.ImmutableList;

import junit.framework.TestCase;
import static com.google.closure.plugin.common.SourceFileProperty
    .LOAD_AS_NEEDED;
import static com.google.closure.plugin.common.SourceFileProperty.TEST_ONLY;

@SuppressWarnings("javadoc")
public final class SrcfilesDirsTest extends TestCase {

  @Test
  public static void testGetDefaultProjectSourceDirectory() {
    SrcfilesDirs sfd = new SrcfilesDirs(
        new File("project"),
        ImmutableList.of(file("project/src/main/java")),
        ImmutableList.of(file("project/src/test/java")));
    assertEquals(
        new TypedFile(file("project/src/main/css")),
        sfd.getDefaultProjectSourceDirectory(FileExt.CSS));
    assertEquals(
        new TypedFile(file("project/dep/main/css"), LOAD_AS_NEEDED),
        sfd.getDefaultProjectSourceDirectory(
            FileExt.CSS, LOAD_AS_NEEDED));
    assertEquals(
        new TypedFile(file("project/src/test/css"), TEST_ONLY),
        sfd.getDefaultProjectSourceDirectory(
            FileExt.CSS, TEST_ONLY));
    assertEquals(
        new TypedFile(file("project/dep/test/css"), LOAD_AS_NEEDED, TEST_ONLY),
        sfd.getDefaultProjectSourceDirectory(
            FileExt.CSS, LOAD_AS_NEEDED, TEST_ONLY));
  }


  private static File file(String s) {
    return new File(s.replace('/', File.separatorChar));
  }
}
