package com.google.common.html.plugin.plan;

import java.io.IOException;

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

}
