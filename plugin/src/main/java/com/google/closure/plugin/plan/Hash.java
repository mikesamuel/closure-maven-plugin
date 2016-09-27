package com.google.closure.plugin.plan;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.closure.plugin.common.Sources.Source;
import com.google.common.io.Files;

/**
 * Wraps a byte[] from a hashing function.
 */
public final class Hash implements Serializable {
  private static final long serialVersionUID = -3522511647884072504L;

  private final byte[] bytes;

  Hash(byte[] bytes) {
    this.bytes = Preconditions.checkNotNull(bytes.clone());
  }

  // SHA1 is used by HashStore convention
  private static final String TO_STRING_PREFIX = "SHA1:";

  private static final String hexDigits = "0123456789abcdef";

  /** Reverse of {@link #toString()} */
  Hash(String hex) {
    if (!hex.startsWith(TO_STRING_PREFIX)) {
      throw new IllegalArgumentException("Hash: " + hex);
    }
    int nBytes = (hex.length() - TO_STRING_PREFIX.length()) / 2;
    byte[] decodedBytes = new byte[nBytes];
    for (int i = 0, j = TO_STRING_PREFIX.length(); i < nBytes; ++i) {
      char c0 = hex.charAt(j++);
      char c1 = hex.charAt(j++);
      decodedBytes[i] = (byte) (hexDecode(c0) | (hexDecode(c1) << 4));
    }
    this.bytes = decodedBytes;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(
        TO_STRING_PREFIX.length() + bytes.length * 2);
    sb.append(TO_STRING_PREFIX);
    for (byte b : bytes) {
      sb.append(hexDigits.charAt(b & 0xf));
      sb.append(hexDigits.charAt((b & 0xf0) >>> 4));
    }
    return sb.toString();
  }

  private static int hexDecode(char hexDigit) {
    if ('0' <= hexDigit && hexDigit <= '9') {
      return hexDigit - '0';
    }
    if ('a' <= hexDigit && hexDigit <= 'f') {
      return hexDigit - ('a' - 10);
    }
    if ('A' <= hexDigit && hexDigit <= 'F') {
      return hexDigit - ('A' - 10);
    }
    throw new IllegalArgumentException(
        "Expected hex digit not '" + hexDigit + "'");
  }

  /** The bytes of the hash. */
  public byte[] getBytes() {
    return bytes.clone();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Hash && Arrays.equals(this.bytes, ((Hash) o).bytes);
  }

  @Override
  public int hashCode() {
    return (bytes[0] & 0xff)
        | ((bytes[1] & 0xff) << 8)
        | ((bytes[2] & 0xff) << 8)
        | ((bytes[3] & 0xff) << 8);
  }

  /** Constructs a hash for the serial form ignoring any transient fields. */
  public static Hash hashSerializable(Serializable ser)
  throws NotSerializableException {
    byte[] bytes;
    try (ByteArrayOutputStream bout = new ByteArrayOutputStream()) {
      ObjectOutputStream oout = new ObjectOutputStream(bout);
      oout.writeObject(ser);
      bytes = bout.toByteArray();
    } catch (NotSerializableException ex) {
      throw ex;
    } catch (IOException ex) {
      throw (AssertionError) new AssertionError(
          "IOException writing to in-memory buffer")
          .initCause(ex);
    }

    MessageDigest md = newDigest();
    md.update(bytes);
    return new Hash(md.digest());
  }

  /**
   * A hash that depends upon the file and current contents.
   * <p>
   * CAVEAT:
   * We optimistically assume that file-system race conditions don't lead to
   * a file being changed between its hash being taken and it being loaded by
   * a step.
   * Some steps create flags that they pass to a command line runner, so we
   * can't simultaneously load and hash files consistently in any case.
   * All release candidates should be built from clean.
   */
  public static Hash hash(Source source) throws IOException {
    File file = source.canonicalPath;
    long length = file.length();
    if (length < 0 || length > Integer.MAX_VALUE) {
      throw new IOException(
          "File is too large for in-memory caching and hashing without caching"
          + " or flocking can lead to race conditions");
    }
    int ilength = (int) length;
    assert ilength >= 0;

    // The length is used as a hint so no race condition here.
    ByteArrayOutputStream byteBuf = new ByteArrayOutputStream(ilength);
    Files.copy(file, byteBuf);

    byte[] bytes = byteBuf.toByteArray();
    byteBuf.reset();  // release internal buffer for GC

    MessageDigest md = newDigest();
    md.update(file.getCanonicalPath().getBytes(Charsets.UTF_8));
    md.update(bytes);
    return new Hash(md.digest());
  }

  /**
   * A hash of the given hashables.
   * @param hashables order is significant to the resulting hash.
   * @throws IOException if any {@link Hashable#hash} call throws.
   */
  public static Optional<Hash> hashAllHashables(
      Iterable<? extends Hashable> hashables)
  throws IOException {
    MessageDigest md = newDigest();
    for (Hashable hashable : hashables) {
      Optional<Hash> hash = hashable.hash();
      if (!hash.isPresent()) { return Optional.absent(); }
      md.update(hash.get().bytes);
    }
    return Optional.of(new Hash(md.digest()));
  }

  /**
   * A hash of the given serializable items.
   * @param serializables order is significant to the resulting hash.
   * @throws NotSerializableException if any {@link #hashSerializable}
   *     call throws.
   */
  public static Hash hashAllSerializables(
      Iterable<? extends Serializable> serializables)
  throws NotSerializableException {
    MessageDigest md = newDigest();
    for (Serializable ser : serializables) {
      Hash hash = hashSerializable(ser);
      md.update(hash.bytes);
    }
    return new Hash(md.digest());
  }

  /**
   * A hash of the given hashes.
   * @param hashes order is significant to the resulting hash.
   */
  public static Hash hashAllHashes(Iterable<? extends Hash> hashes) {
    MessageDigest md = newDigest();
    for (Hash hash : hashes) {
      md.update(hash.bytes);
    }
    return new Hash(md.digest());
  }

  /**
   * A hash of the given String.
   */
  public static Hash hashString(String s) {
    MessageDigest md = newDigest();
    md.update(s.getBytes(Charsets.UTF_8));
    return new Hash(md.digest());
  }

  /**
   * A hash of the given bytes.
   */
  public static Hash hashBytes(byte[] bytes) {
    MessageDigest md = newDigest();
    md.update(bytes);
    return new Hash(md.digest());
  }

  /**
   * True iff the two inputs hash to the same value.
   */
  public static <S extends Serializable>
  boolean same(S a, S b) {
    if (a == b) { return true; }
    Hash ah, bh;
    try {
      ah = Hash.hashSerializable(a);
      bh = Hash.hashSerializable(b);
    } catch (NotSerializableException ex) {
      throw (AssertionError) new AssertionError().initCause(ex);
    }
    return ah.equals(bh);
  }


  private static MessageDigest newDigest() {
    MessageDigest md;
    try {
      md = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException ex) {
      throw (AssertionError) new AssertionError(
          "SHA-1 unavailable")
          .initCause(ex);
    }
    return md;
  }
}