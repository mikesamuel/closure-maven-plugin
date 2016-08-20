package com.google.closure.plugin.proto;

import org.junit.Test;

import com.google.common.base.Optional;
import com.google.closure.plugin.common.CStyleLexer;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class ProtoPackageMapTest extends TestCase {

  @Test
  public static void testNoPackage() {
    assertEquals(
        Optional.<String>absent(),
        ProtoPackageMap.getPackage(
            new CStyleLexer("")));
    assertEquals(
        Optional.<String>absent(),
        ProtoPackageMap.getPackage(
            new CStyleLexer("package = 0;")));
    assertEquals(
        Optional.<String>absent(),
        ProtoPackageMap.getPackage(
            new CStyleLexer("//package foo.bar.baz;")));
    assertEquals(
        Optional.<String>absent(),
        ProtoPackageMap.getPackage(
            new CStyleLexer("{ ... }")));
  }


  @Test
  public static void testGetSimplePackage() {
    assertEquals(
        Optional.of("foo.bar.baz"),
        ProtoPackageMap.getPackage(
            new CStyleLexer("package foo.bar.baz;")));
  }

  @Test
  public static void testPackageSplitAcrossMultipleLines() {
    assertEquals(
        Optional.of("foo.bar.baz"),
        ProtoPackageMap.getPackage(
            new CStyleLexer("package foo.bar\n  .baz;")));
  }

  @Test
  public static void testPackageNameWithDigit() {
    assertEquals(
        Optional.of("Foo2.bar_baz"),
        ProtoPackageMap.getPackage(
            new CStyleLexer("  package Foo2.bar_baz;")));
  }

  @Test
  public static void testPackageNameAfterOption() {
    assertEquals(
        Optional.of("foo.bar.baz"),
        ProtoPackageMap.getPackage(
            new CStyleLexer("option (foo) = 1;\npackage foo.bar.baz;")));
  }

}
