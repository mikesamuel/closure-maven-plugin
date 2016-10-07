package com.google.closure.plugin.js;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;

/**
 * An abstract operation that can be provisioned with stdin, stdout, stderr
 * streams.
 */
abstract class Streamer {

  abstract void stream(
      InputStream stdin, PrintStream stdout, PrintStream stderr)
  throws MojoExecutionException;

  final void stream(
      ByteSource input, ByteSink output, ByteSink error)
  throws IOException, MojoExecutionException {
    try (InputStream in = input.openBufferedStream()) {
      try (OutputStream out = output.openBufferedStream()) {
        try (PrintStream pout = new PrintStream(out, true, "UTF-8")) {
          try (OutputStream err = error.openBufferedStream()) {
            try (PrintStream perr = new PrintStream(err, true, "UTF-8")) {
              stream(in, pout, perr);
            }
          }
        }
      }
    }
  }
}
