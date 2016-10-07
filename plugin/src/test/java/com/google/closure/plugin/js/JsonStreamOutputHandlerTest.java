package com.google.closure.plugin.js;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.maven.plugin.MojoExecutionException;
import org.json.simple.parser.ParseException;
import org.junit.Test;

import com.google.closure.plugin.TestLog;
import com.google.closure.plugin.js.JsonStreamOutputHandler.FileContents;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class JsonStreamOutputHandlerTest extends TestCase {

  @Test
  public static void testHandling() throws Exception {
    JsonStreamOutputHandler h = new JsonStreamOutputHandler(new TestLog());

    final AtomicBoolean hasErrors = new AtomicBoolean();
    @SuppressWarnings("resource")
    final OutputStream o = h.openBufferedStream();
    new Thread(new Runnable() {

      @Override
      public void run() {
        byte[] bytes = ("["
            + "{\"path\":\"/nosuchfile/foo\",\"src\":\"Foo\"},"
            + "{\"path\":\"/nosuchfile/bar\",\"src\":\"Bar\"}"
            + "]")
            .getBytes(Charsets.UTF_8);
        try {
          o.write(bytes);
          o.close();
        } catch (IOException ex) {
          hasErrors.set(true);
          ex.printStackTrace();
        }
      }

    }).start();

    h.waitUntilAllClosed();
    assertFalse(hasErrors.get());

    assertTrue(h.getFailures().toString(), h.getFailures().isEmpty());

    assertEquals(
        ImmutableList.of(
            new FileContents("/nosuchfile/foo", "Foo"),
            new FileContents("/nosuchfile/bar", "Bar")
            ),
        h.getOutputs());
  }

  @Test
  public static void testHandlingOfMalformedOutput() throws Exception {
    JsonStreamOutputHandler h = new JsonStreamOutputHandler(new TestLog());

    final AtomicBoolean hasErrors = new AtomicBoolean();
    @SuppressWarnings("resource")
    final OutputStream o = h.openBufferedStream();
    new Thread(new Runnable() {

      @Override
      public void run() {
        byte[] bytes = ("["
            + "{\"path\":\"/nosuchfile/foo\";\"src\":\"Foo\"},"
            + "{\"path:\"/nosuchfile/bar\";\"src\":\"Bar\"}"
            + "]]")
            .getBytes(Charsets.UTF_8);
        try {
          o.write(bytes);
          o.close();
        } catch (IOException ex) {
          hasErrors.set(true);
          ex.printStackTrace();
        }
      }

    }).start();

    h.waitUntilAllClosed();
    assertFalse(hasErrors.get());  // Only tests writing to stream


    assertFalse(h.getFailures().isEmpty());
    MojoExecutionException ex = h.getFailures().get(0);
    assertNotNull(ex);
    assertTrue(
        ex.getCause().toString(), ex.getCause() instanceof ParseException);
  }

}
