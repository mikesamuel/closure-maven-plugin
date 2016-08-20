package com.google.closure.plugin.plan;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.closure.plugin.common.Ingredients.FileIngredient;
import com.google.closure.plugin.common.Sources.Source;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

/**
 * Extracts metadata from files reusing old metadata when file hashes match.
 */
public final class FileMetadataMapBuilder {
  private FileMetadataMapBuilder() {}

  /** A loader backed by the file system. */
  public static final Function<Source, ByteSource> REAL_FILE_LOADER =
  new Function<Source, ByteSource>() {
    @Override
    public ByteSource apply(Source s) {
      return Files.asByteSource(s.canonicalPath);
    }
  };

  /**
   * Extracts metadata from files reusing old metadata when file hashes match.
   */
  public static <T extends Serializable>
  ImmutableMap<File, Metadata<T>> updateFromIngredients(
      Map<? extends File, ? extends Metadata<T>> previous,
      Function<Source, ByteSource> loader,
      Extractor<T> extractor,
      Iterable<? extends FileIngredient> files)
  throws IOException {
    return updateFromSources(
        previous, loader, extractor,
        Iterables.transform(files, FileIngredient.GET_SOURCE));
  }

  /**
   * Extracts metadata from files reusing old metadata when file hashes match.
   */
  public static <T extends Serializable>
  ImmutableMap<File, Metadata<T>> updateFromSources(
      Map<? extends File, ? extends Metadata<T>> previous,
      Function<Source, ByteSource> loader,
      Extractor<T> extractor,
      Iterable<? extends Source> sources)
  throws IOException {
    ImmutableMap.Builder<File, Metadata<T>> b = ImmutableMap.builder();
    for (Source s : sources) {
      File mapKey = s.canonicalPath;
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
