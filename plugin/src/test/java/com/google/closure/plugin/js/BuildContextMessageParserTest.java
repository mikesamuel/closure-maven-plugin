package com.google.closure.plugin.js;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;

import org.codehaus.plexus.util.Scanner;
import org.junit.Test;
import org.sonatype.plexus.build.incremental.BuildContext;

import com.google.closure.plugin.TestLog;
import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class BuildContextMessageParserTest extends TestCase {

  @Test
  public static void testSomeMessages() throws Exception {
    MessageCollectingBuildContext bc = new MessageCollectingBuildContext();
    BuildContextMessageParser mp = new BuildContextMessageParser(
        new TestLog(), bc);
    try (OutputStream os = mp.openBufferedStream()) {
      try (Writer w = new OutputStreamWriter(os, Charsets.UTF_8)) {
        w.write("Cruft that should show up on the log\n");
        w.write("foo/bar.js:12:4: WARNING - have a nice day\n");
        w.write("foo/bar.js:25: ERROR - There's a ");
        w.write("problem");
        w.write('\n');
        w.write("     Line of s0urce\n");
        w.write("         -----^");
        w.write("\ninput:123: WARNING - warning");
      }
    }
    assertEquals(
        ImmutableMultimap.<File, Message>builder()
            .put(new File("foo/bar.js"),
                new Message(
                    new File("foo/bar.js"),
                    12, 4, "have a nice day",
                    BuildContext.SEVERITY_WARNING,
                    null))
            .put(new File("foo/bar.js"),
                new Message(
                    new File("foo/bar.js"),
                    25, 0,
                    "There's a problem\n"
                    + "     Line of s0urce\n"
                    + "         -----^",
                    BuildContext.SEVERITY_ERROR,
                    null))
            .put(new File("input"),
                new Message(
                    new File("input"),
                    123, 0, "warning",
                    BuildContext.SEVERITY_WARNING,
                    null))
            .build(),
        bc.messages);
  }

  static final class Message {
    final File file;
    final int line;
    final int col;
    final String message;
    final int severity;
    final Throwable cause;

    Message(
        File file, int line, int col, String message, int severity,
        Throwable cause) {
      this.file = file;
      this.line = line;
      this.col = col;
      this.message = message;
      this.severity = severity;
      this.cause = cause;
    }


    @Override
    public String toString() {
      return file + ":" + line + ":" + col + " : "
          + "" + (severity == BuildContext.SEVERITY_ERROR ? "ERROR" : "WARNING")
          + " - " + message;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + col;
      result = prime * result + ((file == null) ? 0 : file.hashCode());
      result = prime * result + line;
      result = prime * result + ((message == null) ? 0 : message.hashCode());
      result = prime * result + severity;
      result = prime * result + ((cause == null) ? 0 : cause.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      Message other = (Message) obj;
      if (col != other.col) {
        return false;
      }
      if (file == null) {
        if (other.file != null) {
          return false;
        }
      } else if (!file.equals(other.file)) {
        return false;
      }
      if (line != other.line) {
        return false;
      }
      if (message == null) {
        if (other.message != null) {
          return false;
        }
      } else if (!message.equals(other.message)) {
        return false;
      }
      if (severity != other.severity) {
        return false;
      }
      if (cause != other.cause) {
        return false;
      }
      return true;
    }
  }

  static final class MessageCollectingBuildContext implements BuildContext {
    final Multimap<File, Message> messages = ArrayListMultimap.create();

    @Override
    public boolean hasDelta(String relpath) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasDelta(File file) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasDelta(@SuppressWarnings("rawtypes") List relpaths) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void refresh(File file) {
      throw new UnsupportedOperationException();
    }

    @Override
    public OutputStream newFileOutputStream(File file) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public Scanner newScanner(File basedir) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Scanner newDeleteScanner(File basedir) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Scanner newScanner(File basedir, boolean ignoreDelta) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isIncremental() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setValue(String key, Object value) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object getValue(String key) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void addWarning(
        File file, int line, int column, String message, Throwable cause) {
      addMessage(
          file, line, column, message, BuildContext.SEVERITY_WARNING, cause);
    }

    @Override
    public void addError(
        File file, int line, int column, String message, Throwable cause) {
      addMessage(
          file, line, column, message, BuildContext.SEVERITY_ERROR, cause);
    }

    @Override
    public void addMessage(
        File file, int line, int column, String message, int severity,
        Throwable cause) {
      this.messages.put(
          file,
          new Message(file, line, column, message, severity, cause));
    }

    @Override
    public void removeMessages(File file) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isUptodate(File target, File source) {
      throw new UnsupportedOperationException();
    }

  }

}
