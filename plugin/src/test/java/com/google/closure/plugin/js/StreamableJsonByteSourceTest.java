package com.google.closure.plugin.js;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.maven.plugin.logging.Log;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

import com.google.closure.plugin.TestLog;
import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.common.TypedFile;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class StreamableJsonByteSourceTest extends TestCase {

  static final class TestableStreamable extends StreamableJsonByteSource {
    final ImmutableMap<File, String> fileContent;
    TestableStreamable(
        Log log, Iterable<? extends Source> sources,
        ImmutableMap<File, String> fileContent) {
      super(log, sources);
      this.fileContent = fileContent;
    }

    @Override
    protected ByteSource contentOf(Source s) {
      return ByteSource.wrap(
          fileContent.get(s.canonicalPath).getBytes(Charsets.UTF_8));
    }
  }

  @Test
  public static void testJsonContent() throws Exception {
    String aSrc = "// Foo";
    String bSrc = "bar();\nbaz(\"\\\\\")";

    TestableStreamable ts = new TestableStreamable(
        new TestLog(),
        ImmutableList.of(src("a.js"), src("b.js")),
        ImmutableMap.<File, String>of(
            new File("/a.js"), aSrc,
            new File("/b.js"), bSrc)
        );

    byte[] bytes = ByteStreams.toByteArray(ts.openBufferedStream());
    String content = new String(bytes, Charsets.UTF_8);
    assertEquals(
        "["
        + "{\"path\":\"a.js\",\"src\":\"// Foo\"},"
        + "{\"path\":\"b.js\",\"src\":\"bar();\\nbaz(\\\"\\\\\\\\\\\")\"}"
        + "]",
        content);
  }


  @Test
  public static void testJsonWellFormedAndConsistent() throws Exception {
    String aSrc = "// Foo";
    String bSrc = "\"use strict\";\n/*\\*/";

    JSONParser jsonParser = new JSONParser();
    TestableStreamable ts = new TestableStreamable(
        new TestLog(),
        ImmutableList.of(src("a.js"), src("b.js")),
        ImmutableMap.<File, String>of(
            new File("/a.js"), aSrc,
            new File("/b.js"), bSrc)
        );

    Object parsed;
    try (Reader r = new InputStreamReader(
             ts.openBufferedStream(), Charsets.UTF_8)) {
      parsed = jsonParser.parse(r);
    }

    assertTrue(parsed instanceof JSONArray);
    JSONArray items = (JSONArray) parsed;
    assertEquals(2, items.size());

    JSONObject item0 = (JSONObject) items.get(0);
    JSONObject item1 = (JSONObject) items.get(1);

    assertEquals("a.js", item0.get("path"));
    assertEquals(aSrc, item0.get("src"));

    assertEquals("b.js", item1.get("path"));
    assertEquals(bSrc, item1.get("src"));
  }


  private static Source src(String relPath) {
    return new Source(
        new File("/" + relPath),
        // TODO: make this test pass under windows
        new TypedFile(new File("/")),
        new File(relPath));
  }
}
