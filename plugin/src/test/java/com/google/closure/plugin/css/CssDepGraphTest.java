package com.google.closure.plugin.css;

import java.io.File;

import org.junit.Test;

import com.google.closure.plugin.TestLog;
import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.common.TypedFile;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class CssDepGraphTest extends TestCase {

  static final ImmutableMap<String, String> CONTENT = ImmutableMap.of(
      "foo.css",      "@provide 'foo'; @require 'bar';",
      "bar.css",      "@provide 'bar';",
      "boo-main.css", "/* leaf file */ @require 'foo';",
      "baz-main.css", "@provide 'baz';",
      "faz.css",      "/* leaf file */ @require 'baz';");

  private static Source src(String relPath) {
    return new Source(
        new File("/src/" + relPath), new TypedFile(new File("/src")),
        new File(relPath));
  }

  private static final Function<Source, String> GET_REL_PATH =
      new Function<Source, String>() {
        @Override
        public String apply(Source s) {
          return s.relativePath.getPath();
        }
      };

  @Test
  public static void testOrdering() throws Exception {
    CssDepGraph g = new CssDepGraph(
        new TestLog(),
        ImmutableList.of(
            src("foo.css"),
            src("bar.css"),
            src("boo-main.css"),
            src("baz-main.css"),
            src("faz.css"))
        ) {
      @Override
      protected String loadContent(Source s) {
        return CONTENT.get(s.relativePath.getPath());
      }
    };
    assertEquals(
        ImmutableList.of("boo-main.css", "baz-main.css"),
        Lists.transform(g.entryPoints, GET_REL_PATH));
    assertEquals(
        ImmutableList.of("bar.css", "foo.css"),
        Lists.transform(
            g.transitiveClosureDeps(src("foo.css")).allDependencies,
            GET_REL_PATH));
    assertEquals(
        ImmutableList.of("bar.css"),
        Lists.transform(
            g.transitiveClosureDeps(src("bar.css")).allDependencies,
            GET_REL_PATH));
    assertEquals(
        ImmutableList.of("bar.css", "foo.css", "boo-main.css"),
        Lists.transform(
            g.transitiveClosureDeps(src("boo-main.css")).allDependencies,
            GET_REL_PATH));
    assertEquals(
        ImmutableList.of("baz-main.css"),
        Lists.transform(
            g.transitiveClosureDeps(src("baz-main.css")).allDependencies,
            GET_REL_PATH));
    assertEquals(
        ImmutableList.of("baz-main.css", "faz.css"),
        Lists.transform(
            g.transitiveClosureDeps(src("faz.css")).allDependencies,
            GET_REL_PATH));
  }

}
