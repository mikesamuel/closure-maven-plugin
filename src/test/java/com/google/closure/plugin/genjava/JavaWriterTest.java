package com.google.closure.plugin.genjava;

import org.junit.Test;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class JavaWriterTest extends TestCase {

  @Test
  public static void testAppendCode() {
    JavaWriter jw = new JavaWriter();
    jw.appendCode("class Foo {").nl();
    jw.appendCode("Foo() {\n}\n\n");
    jw.appendCode("void f() {}");
    jw.nl();
    jw.appendCode("}\n");
    jw.appendCode("// comment");

    assertEquals(
        ""
        + "class Foo {\n"
        + "  Foo() {\n"
        + "  }\n"
        + "\n"
        + "  void f() {}\n"
        + "}\n"
        + "// comment",

        jw.toJava());
  }

  @Test
  public static void testAppendStringLiteral() {
    JavaWriter jw = new JavaWriter();
    jw.appendCode("{ ")
      .appendStringLiteral("foo")
      .appendCode(", ")
      .appendStringLiteral("bar\nbaz")
      .appendCode(", ")
      .appendStringLiteral("\"boo\\\"")
      .appendCode(" }");

    assertEquals(
        "{ \"foo\", \"bar\\nbaz\", \"\\\"boo\\\\\\\"\" }",

        jw.toJava());
  }

}
