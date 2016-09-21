package com.google.closure.plugin.js;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.junit.Test;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
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
            "--module", "main:1")
    .expectInputs(
            "/dep/closure/goog/base.js")
    .expectArgv(
            // main.b has no external dependencies and contains 2 files
            "--module", "b:2:main")
    .expectInputs(
            // Stable file order.
            "/src/main/js/b/baz.js",
            "/src/main/js/b/boo.js")
    .expectArgv(
            // main.a depends upon main.b and contains 2 files
            "--module", "a:2:main,b")
    .expectInputs(
            // bar.js depends upon foo.js
            "/src/main/js/a/foo.js",
            "/src/main/js/a/bar.js")
    .expectArgv(
            // test.b depends upon main.b
            "--module", "b.test:1:main,b")
    .expectInputs(
            "/src/test/js/b/test.js")
    .expectArgv(
            // test.a depends upon main.a and transitively upon main.b
            // Since main.a depends upon main.b, main.b comes first.
            "--module", "a.test:1:main,b,a")
    .expectInputs(
            "/src/test/js/a/test.js")
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
           "--module", "main:4")
       .expectInputs(
           "/dep/closure/goog/base.js",  // No deps
           "/src/dep/js/a/bar.js",  // Depends on base.js implicitly
           "/src/dep/js/a/baz.js",  // Depends on a/bar.js
           "/src/main/js/a/foo.js"  // Depends on a/baz.js
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
            "--module", "main:1")
        .expectInputs(
            "/dep/closure/goog/base.js")
        .expectArgv(
            "--module", "c:1:main")
        .expectInputs(
            "/src/dep/js/c/baz.js")
        .expectArgv(
            "--module", "a:1:main,c")
        .expectInputs(
            "/src/main/js/a/foo.js"  // Depends on c/baz.js
            )
        .log(new TestLog().verbose(false))
        .run();
  }

  static final class TestBuilder extends AbstractDepTestBuilder<TestBuilder> {

    private final ImmutableList.Builder<String> wantedArgv =
        ImmutableList.builder();
    private final ImmutableList.Builder<String> wantedSources =
        ImmutableList.builder();

    TestBuilder() {
      super(TestBuilder.class);

      fileContent(
          "/dep/closure/goog/base.js",
          "/** @fileoverview @provideGoog */");
      source("/dep/closure/goog", "base.js", SourceFileProperty.LOAD_AS_NEEDED);
    }

    TestBuilder expectArgv(String... args) {
      this.wantedArgv.add(args);
      return this;
    }

    TestBuilder expectInputs(String... canonPaths) {
      this.wantedSources.add(canonPaths);
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
      ImmutableList.Builder<Source> sources = ImmutableList.builder();
      modules.addClosureCompilerFlags(modulesArgv, sources);

      ImmutableList<String> canonPaths = ImmutableList.<String>copyOf(
          Lists.transform(
              sources.build(),
              new Function<Source, String>() {
                @Override
                public String apply(Source s) {
                  return s.canonicalPath.getPath();
                }
              }));

      assertEquals(
          Joiner.on("\n").join(wantedArgv.build()),
          Joiner.on("\n").join(modulesArgv.build()));

      assertEquals(
          Joiner.on("\n").join(wantedSources.build()),
          Joiner.on("\n").join(canonPaths));
    }
  }
}
