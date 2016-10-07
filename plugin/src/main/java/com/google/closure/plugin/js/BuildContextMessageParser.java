package com.google.closure.plugin.js;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.logging.Log;
import org.sonatype.plexus.build.incremental.BuildContext;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.ByteSink;
import com.google.javascript.jscomp.CheckLevel;

/**
 * Parses closure compiler messages from stderr and adds them to a
 * build context.
 */
final class BuildContextMessageParser extends ByteSink {

  final BuildContext buildContext;
  final Log log;

  public BuildContextMessageParser(Log log, BuildContext buildContext) {
    this.log = log;
    this.buildContext = buildContext;
  }

  @Override
  public OutputStream openStream() throws IOException {
    return new ByteArrayOutputStream() {
      @Override
      public synchronized void write(int i) {
        super.write(i);
        if (i == '\n') {
          handleFullLine(false);
        }
      }

      @Override
      public synchronized void write(byte[] b, int off, int len) {
        int start = off;
        int end = start + len;
        for (int i = start; i < end; ++i) {
          if (b[i] == '\n') {
            super.write(b, start, i + 1 - start);
            start = i + 1;
            handleFullLine(false);
          }
        }
        super.write(b, start, end - start);
      }

      @Override
      public void close() throws IOException {
        handleFullLine(true);
        super.close();
      }

      private StringBuilder messageBuffer = new StringBuilder();

      private synchronized void handleFullLine(boolean closing) {
        byte[] bytes = this.toByteArray();
        this.reset();
        String line = new String(bytes, Charsets.UTF_8);
        if (startsMessage(line)) {
          maybeDispatchMessage();
        }
        messageBuffer.append(line);
        if (closing) {
          maybeDispatchMessage();
        }
      }

      private void maybeDispatchMessage() {
        if (messageBuffer.length() != 0) {
          String message = messageBuffer.toString();
          messageBuffer.setLength(0);

          Matcher m = MESSAGE_RE.matcher(message);
          if (m.find()) {
            String file = m.group(1);
            int line = Integer.parseInt(m.group(2), 10);
            String colStr = m.group(3);
            int col = colStr != null ? Integer.parseInt(colStr, 10) : 0;
            CheckLevel cl = CheckLevel.valueOf(m.group(4));
            int severity = BuildContext.SEVERITY_WARNING;
            switch (cl) {
              case ERROR:
                severity = BuildContext.SEVERITY_ERROR;
                break;
              default:
                break;
            }
            String messageText = message.substring(m.end());
            buildContext.addMessage(
                new File(file), line, col, messageText.trim(), severity, null);
          } else {
            log.info("jscomp: " + message);
          }

        }
      }
    };
  }

  static final Pattern MESSAGE_RE = Pattern.compile(
      "^([^\r\n:]*):(\\d+):(?:(\\d+):)? "
      + "(" + Joiner.on("|").join(CheckLevel.values()) + ")"
      + " - ");

  static boolean startsMessage(String line) {
    Matcher m = MESSAGE_RE.matcher(line);
    return m.find();
  }
}