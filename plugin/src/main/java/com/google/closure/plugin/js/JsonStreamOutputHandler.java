package com.google.closure.plugin.js;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.ByteSink;

/**
 * Receives JSON output as per the json_streams format and writes files while
 * updating the bundles list of output files.
 */
final class JsonStreamOutputHandler extends ByteSink {

  private int countOpen = 0;
  final Log log;
  private final List<MojoExecutionException> failures =
      Collections.synchronizedList(
          Lists.<MojoExecutionException>newArrayList());
  private final List<FileContents> outputs = Collections.synchronizedList(
      Lists.<FileContents>newArrayList());

  JsonStreamOutputHandler(Log log) {
    this.log = log;
  }

  void waitUntilAllClosed() throws InterruptedException {
    while (true) {
      synchronized (this) {
        if (countOpen == 0) { break; }
        this.wait();
      }
    }
  }

  ImmutableList<MojoExecutionException> getFailures() {
    synchronized (failures) {
      return ImmutableList.copyOf(failures);
    }
  }

  ImmutableList<FileContents> getOutputs() {
    synchronized (outputs) {
      return ImmutableList.copyOf(outputs);
    }
  }

  void processBytes(byte[] bytes) {
    String json = new String(bytes, Charsets.UTF_8);
    Object parsed;
    try {
      parsed = new JSONParser().parse(json);
    } catch (org.json.simple.parser.ParseException ex) {
      failures.add(new MojoExecutionException(
          "Failed to parse output of closure compiler: " + json, ex));
      return;
    }
    if (!(parsed instanceof JSONArray)) {
      failures.add(new MojoExecutionException(
          "Expected JSON streams array: " + json));
      return;
    }
    JSONArray outputArray = (JSONArray) parsed;
    int index = -1;
    for (Object el : outputArray) {
      ++index;
      if (!(el instanceof JSONObject)) {
        failures.add(new MojoExecutionException(
            "Expected JSON streams array " + index + " : " + json));
        continue;
      }
      JSONObject output = (JSONObject) el;
      String path, fileContents;
      try {
        path = (String) output.get("path");
        fileContents = (String) output.get("src");
      } catch (ClassCastException ex) {
        failures.add(new MojoExecutionException(
            "Malformed JSON streams output " + index + " : " + json, ex));
        continue;
      }
      outputs.add(new FileContents(path, fileContents));
    }
  }

  @Override
  public OutputStream openStream() throws IOException {
    synchronized (this) { ++countOpen; }

    return new ByteArrayOutputStream() {
      boolean closed = false;

      @SuppressWarnings("synthetic-access")
      @Override
      public void close() throws IOException {
        super.close();
        processBytes(this.toByteArray());
        synchronized (JsonStreamOutputHandler.this) {
          if (!closed) {
            --countOpen;
            closed = true;
          }
          if (countOpen == 0) {
            JsonStreamOutputHandler.this.notifyAll();
          }
        }
      }
    };
  }

  static final class FileContents {
    final String path;
    final String contents;

    FileContents(String path, String contents) {
      this.path = Preconditions.checkNotNull(path);
      this.contents = Preconditions.checkNotNull(contents);
    }

    @Override
    public int hashCode() {
      return path.hashCode() + 31 * contents.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof FileContents) {
        FileContents that = (FileContents) obj;
        return this.path.equals(that.path)
            && this.contents.equals(that.contents);
      }
      return false;
    }
  }
}
