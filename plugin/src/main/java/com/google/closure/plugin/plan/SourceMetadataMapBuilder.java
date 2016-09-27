package com.google.closure.plugin.plan;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Comparator;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.closure.plugin.common.Sources.Source;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

/**
 * Extracts metadata from files reusing old metadata when file hashes match.
 */
public final class SourceMetadataMapBuilder {
  private SourceMetadataMapBuilder() {}

  /** A loader backed by the file system. */
  public static final Function<Source, ByteSource> REAL_FILE_LOADER =
  new Function<Source, ByteSource>() {
    @Override
    public ByteSource apply(Source s) {
      return Files.asByteSource(s.canonicalPath);
    }
  };

  static final class CompareByCanonicalFile
  implements Comparator<Source>, Serializable {

    private static final long serialVersionUID = 1L;

    static final CompareByCanonicalFile INSTANCE = new CompareByCanonicalFile();

    @SuppressWarnings("static-method")
    Object readResolve() {
      return INSTANCE;
    }

    @Override
    public int compare(Source o1, Source o2) {
      return o1.canonicalPath.compareTo(o2.canonicalPath);
    }
  }

  /**
   * Extracts metadata from files reusing old metadata when file hashes match.
   */
  public static <T extends Serializable>
  ImmutableMap<Source, Metadata<T>> updateFromSources(
      Map<? extends Source, ? extends Metadata<T>> previous,
      Function<Source, ByteSource> loader,
      Extractor<T> extractor,
      Iterable<? extends Source> sources)
  throws IOException {
    ImmutableMap.Builder<Source, Metadata<T>> b = ImmutableSortedMap.orderedBy(
        CompareByCanonicalFile.INSTANCE);
    for (Source s : sources) {
      Source mapKey = s;
      byte[] content;
      try (InputStream in = loader.apply(s).openStream()) {
        content = ByteStreams.toByteArray(in);
      }
      Hash h = Hash.hashBytes(content);
      Metadata<T> oldMetadata = previous.get(mapKey);
      Metadata<T> newMetadata;
      if (oldMetadata != null && h.equals(oldMetadata.hash)) {
        newMetadata = oldMetadata;
      } else {
        T md = extractor.extractMetadata(s, content);
        newMetadata = new Metadata<>(h, md);
      }
      b.put(mapKey, newMetadata);
    }
    return b.build();
  }

  /**
   * A pure function from a file path and content to metadata.
   */
  public interface Extractor<T extends Serializable> {
    /**
     * The metadata for content of s.
     * @param content the byte content of s.
     */
    T extractMetadata(Source s, byte[] content) throws IOException;
  }
}
