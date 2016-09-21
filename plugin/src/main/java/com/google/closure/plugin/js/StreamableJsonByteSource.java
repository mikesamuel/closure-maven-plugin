package com.google.closure.plugin.js;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;

import org.apache.maven.plugin.logging.Log;

import com.google.closure.plugin.common.Sources.Source;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;

/**
 * Allows streaming JavaScript sources to Closure compiler without loading
 * all the sources in memory at once.
 * <p>
 * <a href="https://github.com/google/closure-compiler/blob/abfa81a881d4b9c68b2c2b20641d6de391d81a7b/src/com/google/javascript/jscomp/CommandLineRunner.java#L616-L626">
 * CommandLineRunner.Flags.jsonStreamMode</a> says
 * <blockquote>
 *   Specifies whether standard input and output streams will be
 *   a JSON array of sources. Each source will be an object of the form
 *   <tt>{path: filename, src: file_contents, srcmap: srcmap_contents }</tt>.
 *   <br>Intended for use by stream-based build systems such as gulpjs.
 * </blockquote>
 * <p>
 * TODO: This is overly complicated, but significantly reduces peak memory load.
 */
class StreamableJsonByteSource extends ByteSource {
  final Log log;
  final ImmutableList<Source> sources;

  StreamableJsonByteSource(Log log, Iterable<? extends Source> sources) {
    this.log = log;
    this.sources = ImmutableList.copyOf(sources);
  }

  enum Stage {
    BEFORE_PATH,
    BEFORE_CONTENT,
    DONE,
  }

  @Override
  public InputStream openStream() throws IOException {
    return new InputStream() {
      /** Buffer for bytes we need to produce */
      private byte[] buf = new byte[1024];
      /** The position in buf of the next byte to produce if < limit. */
      private int pos;
      /** The position after the last byte in buf that still needs to be
       * produced or == pos if none.
       */
      private int limit;

      /**
       * True iff we have written a source, so need a comma before the next.
       */
      private boolean wroteOne = false;
      /**
       * Sources that have not yet been emitted.
       */
      private final Iterator<Source> remaining = sources.iterator();
      /** How much of the current source have we emitted? */
      private StreamableJsonByteSource.Stage stage = Stage.DONE;
      /**
       * The current source or null if not in the middle of a source
       * object.
       */
      private Source current = null;

      /**
       * A stream to drain as string content.
       */
      private InputStream reading = null;
      /**
       * The set of source file names used so we can disambiguate on the fly.
       */
      private Map<String, Source> byRelName = Maps.newHashMap();
      /**
       * True if we have finished all sources and pushed the closing suffix.
       */
      private boolean finished;

      {
        push("[");
      }

      @Override
      public int read() throws IOException {
        if (pos < limit) {
          // If we have buffered content, drain that first.
          return buf[pos++];
        }
        pos = limit = 0;
        if (finished) {
          return -1;
        }
        if (reading != null) {
          // Escape the stream as JSON string content.
          int r = reading.read();
          if (r < 0) {
            reading.close();
            reading = null;
          } else {
            int esc = -1;
            switch (r) {
              case '\n': esc = 'n'; break;
              case '\r': esc = 'r'; break;
              case '\\': case '"': esc = r; break;
            }
            if (esc == -1) {
              return (byte) r;
            } else {
              limit = 1;
              buf[0] = (byte) esc;
              return (byte) '\\';
            }
          }
        }
        Preconditions.checkState(reading == null);
        switch (stage) {
          case BEFORE_PATH:
            Preconditions.checkNotNull(current);
            stage = Stage.BEFORE_CONTENT;
            push(",\"src\":\"");
            // Assume UTF-8.
            reading = contentOf(current).openBufferedStream();
            return (byte) '"';  // Close string containing path.
          case BEFORE_CONTENT:
            Preconditions.checkNotNull(current);
            stage = Stage.DONE;
            this.wroteOne = true;
            push("}");
            current = null;
            // TODO: Do we need source map on input?
            return (byte) '"';  // Close string containing content.
          case DONE:
            Preconditions.checkState(current == null);
            if (remaining.hasNext()) {
              stage = Stage.BEFORE_PATH;
              current = Preconditions.checkNotNull(remaining.next());
              char nextChar = '{';
              if (this.wroteOne) {
                nextChar = ',';
                push("{");
              }
              push("\"path\":\"");

              // Closure compiler requires that source paths uniquely identify
              // the compilation unit, so disambiguate.
              String relPath = current.relativePath.getPath();
              String uniquePath = relPath;
              for (int ctr = 0; byRelName.containsKey(uniquePath); ++ctr) {
                uniquePath = relPath + "#" + ctr;
              }
              if (!relPath.equals(uniquePath)) {
                log.warn(
                    "Source " + current.canonicalPath
                    + " has the same relative path as "
                    + byRelName.get(relPath));
              }
              byRelName.put(uniquePath, current);

              this.reading = new ByteArrayInputStream(
                  uniquePath.getBytes(Charsets.UTF_8));
              return (byte) nextChar;
            } else {
              finished = true;
              return (byte) ']';  // Close the list
            }
        }
        throw new AssertionError(stage);
      }

      void push(String s) {
        for (int i = 0, n = s.length(); i < n; ++i) {
          char ch = s.charAt(i);
          Preconditions.checkState(ch < 0x80);  // UTF-8/ASCII compatible
          buf[limit++] = (byte) ch;
        }
      }

      @Override
      public void close() throws IOException {
        this.pos = limit = 0;
        this.finished = true;
        if (reading != null) {
          reading.close();
          reading = null;
        }
      }
    };
  }

  /**
   * Fetches from the file system.  May be overridden for testing.
   */
  @SuppressWarnings("static-method")
  protected ByteSource contentOf(Source source) {
    return Files.asByteSource(source.canonicalPath);
  }
}
