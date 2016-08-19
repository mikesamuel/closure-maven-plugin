package com.google.closure.plugin.js;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.closure.plugin.common.TopoSort;
import com.google.closure.plugin.TestLog;
import com.google.closure.plugin.common.SourceFileProperty;
import com.google.closure.plugin.common.Sources.Source;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class ComputeJsDepGraphTest extends TestCase {

  @Test
  public static void testModules() throws Exception {
    new TestBuilder()
    .fileContent("/src/main/js/a/foo.js",
        ""
        + "goog.module('a');\n"
        + "goog.provide('a.foo');")

    .fileContent("/src/main/js/a/bar.js",
        ""
        + "goog.module('a');\n"
        + "goog.provide('a.bar');\n"
        + "goog.require('a.foo');\n"
        + "goog.require('b.boo');")

    .fileContent("/src/main/js/b/baz.js",
        ""
        + "goog.module('b');\n"
        + "goog.provide('b.baz');")

    .fileContent("/src/main/js/b/boo.js",
        ""
        + "goog.module('b');\n"
        + "goog.provide('b.boo');")

    .fileContent("/src/test/js/a/test.js",
        ""
        + "goog.module('a.test');\n"
        + "goog.provide('a.test');\n"
        + "goog.require('a.foo');\n"
        + "goog.require('a.bar');")

    .fileContent("/src/test/js/b/test.js",
        ""
        + "goog.module('b.test');\n"
        + "goog.provide('b.test');\n"
        + "goog.require('b.baz');\n"
        + "goog.require('b.boo');")

    .mainSource("/src/main/js", "a/foo.js")
    .mainSource("/src/main/js", "a/bar.js")
    .mainSource("/src/main/js", "b/baz.js")
    .mainSource("/src/main/js", "b/boo.js")
    .testSource("/src/test/js", "a/test.js")
    .testSource("/src/test/js", "b/test.js")
    .expectArgv(
            // We get a main module first that just includes the basic closure
            // primitives like goog.require & goog.provide.
            "--module", "main:1",
            "--js", "/dep/closure/goog/base.js",

            // main.b has no external dependencies and contains 2 files
            "--module", "b:2:main",
            // Stable file order.
            "--js", "/src/main/js/b/baz.js",
            "--js", "/src/main/js/b/boo.js",

            // main.a depends upon main.b and contains 2 files
            "--module", "a:2:main,b",
            // bar.js depends upon foo.js
            "--js", "/src/main/js/a/foo.js",
            "--js", "/src/main/js/a/bar.js",

            // test.b depends upon main.b
            "--module", "b.test:1:main,b",
            "--js", "/src/test/js/b/test.js",

            // test.a depends upon main.a and transitively upon main.b
            // Since main.a depends upon main.b, main.b comes first.
            "--module", "a.test:1:main,b,a",
            "--js", "/src/test/js/a/test.js"
            )
    .run();
  }

  @Test
  public static void testModulesWithMissingDep() throws Exception {
    try {
      new TestBuilder()
      .fileContent(
          "/src/main/js/foo.js",
          ""
          + "goog.provide('foo');\n"
          + "goog.require('bar');\n")
      .mainSource("/src/main/js", "foo.js")
      .run();
    } catch (MojoExecutionException ex) {
      assertTrue(
          ex.getCause().getMessage(),
          ex.getCause() instanceof TopoSort.MissingRequirementException);
      return;
    }
    fail("Expected exception");
  }

  @Test
  public static void testModulesWithModuleCycle() throws Exception {
    try {
      new TestBuilder()
      .fileContent(
          "/src/main/js/a/foo.js",
          ""
          + "goog.module('a');\n"
          + "goog.require('b');\n")
      .fileContent(
          "/src/main/js/b/bar.js",
          ""
          + "goog.module('b');\n"
          + "goog.require('a');\n")
      .mainSource("/src/main/js", "a/foo.js")
      .mainSource("/src/main/js", "b/bar.js")
      .run();
    } catch (MojoExecutionException ex) {
      assertTrue(
          ex.getCause().getMessage(),
          ex.getCause() instanceof TopoSort.CyclicRequirementException);
      assertEquals(
          // b depends, via goog.require('a') on a
          "[{ModuleName b}, {GoogNamespace a}, "
          // a depends, via goog.require('b'), on b
          + "{ModuleName a}, {GoogNamespace b},"
          + " {ModuleName b}]",
          ((TopoSort.CyclicRequirementException) ex.getCause())
          .cycle.toString());
      return;
    }
    fail("Expected exception");
  }

  @Test
  public static void testModulesWithInternalCycle() throws Exception {
    try {
      new TestBuilder()
      .fileContent(
          "/src/main/js/a/foo.js",
          ""
          + "goog.provide('a.foo');\n"
          + "goog.require('a.bar');\n")
      .fileContent(
          "/src/main/js/a/bar.js",
          ""
          + "goog.provide('a.bar');\n"
          + "goog.require('a.foo');\n")
      .mainSource("/src/main/js", "a/foo.js")
      .mainSource("/src/main/js", "a/bar.js")
      .run();
    } catch (MojoExecutionException ex) {
      assertTrue(
          ex.getCause().getMessage(),
          ex.getCause() instanceof TopoSort.CyclicRequirementException);
      assertEquals(
          // foo.js depends, via a.bar on bar.js
          "[{Source /src/main/js/a/foo.js}, {GoogNamespace a.bar}, "
          // bar.js depends, via a.foo, on foo.js
          + "{Source /src/main/js/a/bar.js}, {GoogNamespace a.foo},"
          + " {Source /src/main/js/a/foo.js}]",
          ((TopoSort.CyclicRequirementException) ex.getCause())
          .cycle.toString());
      return;
    }
    fail("Expected exception");
  }

  @Test
  public static void testDeps() throws Exception {
    new TestBuilder()
       .source("/src/main/js", "a/foo.js")
       .source("/src/dep/js", "a/bar.js", SourceFileProperty.LOAD_AS_NEEDED)
       .source("/src/dep/js", "a/baz.js", SourceFileProperty.LOAD_AS_NEEDED)
       .source("/src/dep/js", "a/boo.js", SourceFileProperty.LOAD_AS_NEEDED)
       .fileContent(
           "/src/main/js/a/foo.js",
           ""
           + "goog.provide('a.foo');\n"
           + "goog.require('a.baz');")
       .fileContent(
           "/src/dep/js/a/bar.js",
           "goog.provide('a.bar');")
       .fileContent(
           "/src/dep/js/a/baz.js",
           ""
           + "goog.provide('a.baz');\n"
           + "goog.require('a.bar');")
       .fileContent(
           "/src/dep/js/a/boo.js",
           ""
           + "goog.provide('a.boo');\n"
           + "goog.require('a.bar');")
       .expectArgv(
           "--module", "main:4",
           "--js", "/dep/closure/goog/base.js",  // No deps
           "--js", "/src/dep/js/a/bar.js",  // Depends on base.js implicitly
           "--js", "/src/dep/js/a/baz.js",  // Depends on a/bar.js
           "--js", "/src/main/js/a/foo.js"  // Depends on a/baz.js
           )
       .log(new TestLog().verbose(false))
       .run();
  }

  @Test
  public static final void testUnusedModules() throws Exception {
    new TestBuilder()
        .source("/src/main/js", "a/foo.js")
        .source("/src/dep/js", "b/bar.js", SourceFileProperty.LOAD_AS_NEEDED)
        .source("/src/dep/js", "c/baz.js", SourceFileProperty.LOAD_AS_NEEDED)
        .source("/src/dep/js", "d/boo.js", SourceFileProperty.LOAD_AS_NEEDED)
        .fileContent(
            "/src/main/js/a/foo.js",
            ""
            + "goog.module('a');\n"
            + "goog.require('c');")
        .fileContent(
            "/src/dep/js/b/bar.js",
            "goog.module('b');")
        .fileContent(
            "/src/dep/js/c/baz.js",
            "goog.module('c');")
        .fileContent(
            "/src/dep/js/d/boo.js",
            ""
            + "goog.module('d');\n"
            + "goog.require('b');")
        .expectArgv(
            "--module", "main:1",
            "--js", "/dep/closure/goog/base.js",
            "--module", "c:1:main",
            "--js", "/src/dep/js/c/baz.js",
            "--module", "a:1:main,c",
            "--js", "/src/main/js/a/foo.js"  // Depends on c/baz.js
            )
        .log(new TestLog().verbose(false))
        .run();
  }

  static final class TestBuilder extends AbstractDepTestBuilder<TestBuilder> {

    private ImmutableList.Builder<String> wantedArgv = ImmutableList.builder();

    TestBuilder() {
      super(TestBuilder.class);

      fileContent(
          "/dep/closure/goog/base.js",
          "/** @fileoverview @provideGoog */");
      source("/dep/closure/goog", "base.js", SourceFileProperty.LOAD_AS_NEEDED);
    }

    TestBuilder expectArgv(String... args) {
      for (String arg : args) {
        this.wantedArgv.add(arg);
      }
      return this;
    }

    @Override
    void run(
        Log log, JsOptions options, ImmutableList<Source> srcs, JsDepInfo di)
    throws MojoExecutionException {
      Modules modules = ComputeJsDepGraph.computeDepGraph(
          log,
          options,
          srcs,
          di);

      ImmutableList.Builder<String> modulesArgv = ImmutableList.builder();
      modules.addClosureCompilerFlags(modulesArgv);

      assertEquals(
          Joiner.on("\n").join(wantedArgv.build()),
          Joiner.on("\n").join(modulesArgv.build()));
    }
  }
}
