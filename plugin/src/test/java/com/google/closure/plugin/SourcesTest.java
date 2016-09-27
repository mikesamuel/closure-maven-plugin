package com.google.closure.plugin;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.maven.plugin.logging.Log;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.closure.plugin.common.DirectoryScannerSpec;
import com.google.closure.plugin.common.Sources;
import com.google.closure.plugin.common.TypedFile;
import com.google.closure.plugin.common.Sources.Source;
import com.google.common.io.Files;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class SourcesTest extends TestCase {
  private File tmpDir, bar, foo;
  private final Log log = new TestLog();
  private TypedFile tmpDirRoot;


  @Before
  @Override
  public void setUp() throws IOException {
    tmpDir = Files.createTempDir().getCanonicalFile();
    tmpDirRoot = new TypedFile(tmpDir);
    // T/bar/foo.txt
    // T/bar/baz.txt/
    // T/boo.txt
    // T/foo/bar.txt
    // T/foo/bar.not
    // T/not.txt/
    bar = new File(tmpDir, "bar");
    foo = new File(tmpDir, "foo");
    assertTrue(bar.mkdirs());
    assertTrue(foo.mkdirs());
    assertTrue(new File(bar, "foo.txt").createNewFile());
    assertTrue(new File(bar, "baz.txt").createNewFile());
    assertTrue(new File(tmpDir, "boo.txt").createNewFile());
    assertTrue(new File(foo, "bar.txt").createNewFile());
    assertTrue(new File(foo, "bar.not").createNewFile());
    assertTrue(new File(tmpDir, "not.txt").mkdirs());
  }

  @Test
  public final void testScan() throws Exception {
    DirectoryScannerSpec spec = new DirectoryScannerSpec(
        ImmutableList.of(tmpDirRoot),
        ImmutableList.of("**/*.txt"),
        ImmutableList.<String>of());
    Sources sources = Sources.scan(log, spec);
    Map<File, Source> actual = Maps.newLinkedHashMap();
    for (Source source : sources.sources) {
      assertTrue(
          source.toString(),
          !actual.containsKey(source.relativePath));
      actual.put(source.relativePath, source);
    }

    Map<File, Source> expected = ImmutableMap.<File, Source>builder()
        .put(
            new File("bar", "foo.txt"),
            new Source(
                new File(bar, "foo.txt"),
                tmpDirRoot,
                new File("bar", "foo.txt")))
        .put(
            new File("bar", "baz.txt"),
            new Source(
                new File(bar, "baz.txt"),
                tmpDirRoot,
                new File("bar", "baz.txt")))
        .put(
            new File("boo.txt"),
            new Source(
                new File(tmpDir, "boo.txt"),
                tmpDirRoot,
                new File("boo.txt")))
        .put(
            new File("foo", "bar.txt"),
            new Source(
                new File(foo, "bar.txt"),
                tmpDirRoot,
                new File("foo", "bar.txt")))
        .build();

    for (ImmutableList<Map<File, Source>> mapPair : ImmutableList.of(
        ImmutableList.of(expected, actual),
        ImmutableList.of(actual, expected))) {
      Map<File, Source> superMap = mapPair.get(0);
      Map<File, Source> subMap = mapPair.get(1);
      for (Map.Entry<File, Source> e : superMap.entrySet()) {
        File key = e.getKey();
        Source ssub = subMap.get(key);

        if (ssub == null) {
          fail(
              superMap == expected
              ? "Expected entry for " + key
              : "Spurious entry for " + key);
        } else {
          Source ssuper = e.getValue();
          assertEquals(key + " canonical path",
              ssub.canonicalPath.toString(), ssuper.canonicalPath.toString());
          assertEquals(key + " relative path",
              ssub.relativePath.toString(), ssuper.relativePath.toString());
          assertEquals(key + " root",
              ssub.root.toString(), ssuper.root.toString());
        }
      }
    }
    assertEquals(expected, actual);
  }

  @Test
  public final void testResolve() throws URISyntaxException, IOException {
    Source srcFoo = new Source(
        new File(bar, "foo.txt"),
        tmpDirRoot,
        new File("bar", "foo.txt"));
    Source srcBar = new Source(
        new File(foo, "bar.txt"),
        tmpDirRoot,
        new File("foo", "bar.txt"));

    {
      Source[] inRoots = new Source[] {
          srcFoo.resolve("../in-root.txt"),
          srcFoo.resolve("/in-root.txt"),
      };
      for (int i = 0; i < inRoots.length; ++i) {
        Source inRoot = inRoots[i];
        assertEquals(
            "" + i,
            new File(tmpDir, "in-root.txt").getPath(),
            inRoot.canonicalPath.getPath());
        assertEquals(
            "" + i,
            new File("in-root.txt").getPath(),
            inRoot.relativePath.getPath());
        assertEquals(
            "" + i,
            tmpDir.getPath(),
            inRoot.root.f.getPath());
      }
    }

    {
      Source[] resolvedBars = {
          srcFoo.resolve("../foo/bar.txt"),
          srcFoo.resolve("../bar/../foo/bar.txt"),
          srcFoo.resolve("/foo/bar.txt"),
      };
      for (int i = 0; i < resolvedBars.length; ++i) {
        Source resolvedBar = resolvedBars[i];
        assertEquals(
            "" + i,
            srcBar.root.f.getPath(),
            resolvedBar.root.f.getPath());
        assertEquals(
            "" + i,
            srcBar.canonicalPath.getPath(),
            resolvedBar.canonicalPath.getPath());
        assertEquals(
            "" + i,
            srcBar.relativePath.getPath(),
            resolvedBar.relativePath.getPath());
      }
    }
  }

}
