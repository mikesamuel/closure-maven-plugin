package com.google.common.html.plugin.js;

import java.io.File;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.html.plugin.TestLog;
import com.google.common.html.plugin.common.SourceFileProperty;
import com.google.common.html.plugin.common.TopoSort;
import com.google.common.html.plugin.common.TypedFile;
import com.google.common.html.plugin.common.Sources.Source;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.SourceFile;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class ComputeJsDepGraphTest extends TestCase {

  @Test
  public static void testModules() throws Exception {
    new TestBuilder()
    .fileContent("/src/main/js/a/foo.js",
        "goog.provide('a.foo');")

    .fileContent("/src/main/js/a/bar.js",
        ""
        + "goog.provide('a.bar');\n"
        + "goog.require('a.foo');\n"
        + "goog.require('b.boo');")

    .fileContent("/src/main/js/b/baz.js",
        "goog.provide('b.baz');")

    .fileContent("/src/main/js/b/boo.js",
        "goog.provide('b.boo');")

    .fileContent("/src/test/js/a/test.js",
        ""
        + "goog.provide('a.test');\n"
        + "goog.require('a.foo');\n"
        + "goog.require('a.bar');")

    .fileContent("/src/test/js/b/test.js",
        ""
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
            // main.b has no external dependencies and contains 2 files
            "--module", "main.b:2",
            // Stable file order.
            "--js", "/src/main/js/b/baz.js",
            "--js", "/src/main/js/b/boo.js",

            // main.a depends upon main.b and contains 2 files
            "--module", "main.a:2:main.b",
            // bar.js depends upon foo.js
            "--js", "/src/main/js/a/foo.js",
            "--js", "/src/main/js/a/bar.js",

            // test.b depends upon main.b
            "--module", "test.b:1:main.b",
            "--js", "/src/test/js/b/test.js",

            // test.a depends upon main.a and transitively upon main.b
            // Since main.a depends upon main.b, main.b comes first.
            "--module", "test.a:1:main.b,main.a",
            "--js", "/src/test/js/a/test.js"
            )
    .run();
  }

  @Test
  public static void testModulesWithMissingDep() {
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
  public static void testModulesWithModuleCycle() {
    try {
      new TestBuilder()
      .fileContent(
          "/src/main/js/a/foo.js",
          ""
          + "goog.provide('a.foo');\n"
          + "goog.require('b.bar');\n")
      .fileContent(
          "/src/main/js/b/bar.js",
          ""
          + "goog.provide('b.bar');\n"
          + "goog.require('a.foo');\n")
      .mainSource("/src/main/js", "a/foo.js")
      .mainSource("/src/main/js", "b/bar.js")
      .run();
    } catch (MojoExecutionException ex) {
      assertTrue(
          ex.getCause().getMessage(),
          ex.getCause() instanceof TopoSort.CyclicRequirementException);
      assertEquals(
          // b depends, via a.foo on a
          "[{ModuleName main.b}, {GoogNamespace a.foo}, "
          // a depends, via b.bar, on b
          + "{ModuleName main.a}, {GoogNamespace b.bar},"
          + " {ModuleName main.b}]",
          ((TopoSort.CyclicRequirementException) ex.getCause())
          .cycle.toString());
      return;
    }
    fail("Expected exception");
  }

  @Test
  public static void testModulesWithInternalCycle() {
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


  static final class TestBuilder {

    private Map<String, String> fileContent = Maps.newLinkedHashMap();
    private ImmutableList.Builder<Source> sources = ImmutableList.builder();
    private ImmutableList.Builder<String> wantedArgv = ImmutableList.builder();

    private static Source src(
        String root, String relPath, SourceFileProperty... ps) {
      return new Source(
          new File(root + "/" + relPath),
          new TypedFile(new File(root), ps),
          new File(relPath));
    }

    TestBuilder fileContent(String path, String content) {
      Preconditions.checkState(null == fileContent.put(path, content));
      return this;
    }

    TestBuilder mainSource(String root, String relPath) {
      sources.add(src(root, relPath));
      return this;
    }

    TestBuilder testSource(String root, String relPath) {
      sources.add(src(root, relPath, SourceFileProperty.TEST_ONLY));
      return this;
    }

    TestBuilder expectArgv(String... args) {
      for (String arg : args) {
        this.wantedArgv.add(arg);
      }
      return this;
    }

    void run() throws MojoExecutionException {
      Modules modules = ComputeJsDepGraph.execute(
          new TestLog(),
          new JsOptions(),
          new ComputeJsDepGraph.CompilerInputFactory() {
            @Override
            public CompilerInput create(Source source) {
              @SuppressWarnings("synthetic-access")
              String content = Preconditions.checkNotNull(
                  fileContent.get(source.canonicalPath.getPath()),
                  source.canonicalPath.getPath());
              SourceFile sf = SourceFile.builder()
                  .withOriginalPath(source.relativePath.getPath())
                  .buildFromCode(source.canonicalPath.getPath(), content);
              return new CompilerInput(sf);
            }
          },
          sources.build());

      ImmutableList.Builder<String> modulesArgv = ImmutableList.builder();
      modules.addClosureCompilerFlags(modulesArgv);


      assertEquals(
          Joiner.on("\n").join(wantedArgv.build()),
          Joiner.on("\n").join(modulesArgv.build()));
    }
  }
}
