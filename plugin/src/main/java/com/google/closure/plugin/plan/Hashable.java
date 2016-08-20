package com.google.closure.plugin.plan;

import java.io.IOException;

import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Optional;

/** Can be hashed. */
public interface Hashable {

  /**
   * A hash of this ingredients state.
   *
   * @return may be absent if this represents a resource like a file which has
   *     not yet been created.
   * @throws IOException if fetching bytes failed.  FileNotFoundException should
   *     result in an absent value, not be thrown.
   */
  public abstract Optional<Hash> hash() throws IOException;

  /**
   * A hashable which knows how to transition from an absent hash state to
   * a present hash state.
   */
  public interface AutoResolvable extends Hashable {
    /**
     * If called when <code>!{@link #hash()}.isPresent()</code>, makes a
     * best effort to make it {@linkplain Optional#isPresent() present}.
     */
    void resolve(Log log) throws IOException;
  }
}
