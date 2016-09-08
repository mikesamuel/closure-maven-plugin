package com.google.closure.module;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

import com.google.common.base.Preconditions;
import com.google.template.soy.jbcsrc.api.AdvisingAppendable;

/**
 * A simple AdvisingAppendable that delegates to an Appendable without trying
 * to determine whether
 */
public class ResponseWriter
implements AdvisingAppendable, Flushable, Closeable {
  protected final Appendable out;

  /** */
  public ResponseWriter(Appendable out) {
    // We don't want to spuriously throw Exception from close and we don't want
    // to fail to close resources.
    Preconditions.checkArgument(
        (out instanceof Closeable) == (out instanceof AutoCloseable),
        "this.close() will not close an AutoCloseable appendable that is not"
        + " java.io.Closeable");
    this.out = Preconditions.checkNotNull(out);
  }

  @Override
  public ResponseWriter append(CharSequence s) throws IOException {
    out.append(s);
    return this;
  }

  @Override
  public ResponseWriter append(char ch) throws IOException {
    out.append(ch);
    return this;
  }

  @Override
  public ResponseWriter append(CharSequence s, int lt, int rt)
      throws IOException {
    out.append(s, lt, rt);
    return this;
  }

  @Override
  public boolean softLimitReached() {
    return false;
  }

  @Override
  public void flush() throws IOException {
    if (out instanceof Flushable) {
      ((Flushable) out).flush();
    }
  }

  @Override
  public void close() throws IOException {
    if (out instanceof Closeable) {
      ((Closeable) out).close();
    }
  }
}
