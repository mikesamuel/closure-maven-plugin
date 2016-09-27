package com.google.closure.plugin.plan;

/** Can be hashed. */
public interface Hashable {

  /**
   * A hash of this ingredients state.
   *
   * @return may be absent if this represents a resource like a file which has
   *     not yet been created.
   */
  Hash hash();


  static abstract class RecursivelyHashable implements Hashable {
    /** Hashes by creating a builder for {@link #hash(Hash.Builder)}. */
    @Override
    public final Hash hash() {
      // Not actually recursive.
      return new Hash.Builder().hash(this).build();
    }

    protected abstract void hash(Hash.Builder hb);
  }
}
