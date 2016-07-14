package com.google.common.html.plugin.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.html.plugin.Options;
import com.google.common.html.plugin.Sources;
import com.google.common.html.plugin.Sources.Source;
import com.google.common.html.plugin.plan.Hash;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.inject.Provider;

/**
 * Pools ingredients based on key.
 */
public class Ingredients {

  private final Cache<String, Ingredient> ingredients =
      CacheBuilder.newBuilder()
      // TODO: is this right?
      .weakValues()
      .build();

  /**
   * Lazily allocate an ingredient with the given key based on a spec.
   */
  public <T extends Ingredient>
  T get(Class<T> type, String key, final Provider<? extends T> maker) {
    Ingredient got;
    try {
      got = ingredients.get(key, new Callable<T>() {
        public T call() {
          return maker.get();
        }
      });
    } catch (ExecutionException ex) {
      throw (AssertionError) new AssertionError().initCause(ex);
    }
    Preconditions.checkState(key.equals(got.key));
    return type.cast(got);
  }

  private static Source singletonSource(File file) throws IOException {
    File canonFile = file.getCanonicalFile();

    class RootFinder {
      File root = null;
      File deroot(File f) {
        File parent = f.getParentFile();
        if (parent == null) {
          root = f;
          return null;
        } else {
          return new File(deroot(parent), f.getName());
        }
      }
    }

    RootFinder rf = new RootFinder();
    File relFile = rf.deroot(file);
    if (relFile == null) {
      throw new IOException("The file-system root cannot be a source file");
    }
    return new Sources.Source(canonFile, rf.root, relFile);
  }

  /** An ingredient backed by a file which is hashable when the file exists. */
  public FileIngredient file(File file) throws IOException {
    return file(singletonSource(file));
  }

  /** An ingredient backed by a file which is hashable when the file exists. */
  public FileIngredient file(final Source source) {
    final String key = "file:" + source.canonicalPath;
    return get(
        FileIngredient.class,
        key,
        new Provider<FileIngredient>() {
          @SuppressWarnings("synthetic-access")
          public FileIngredient get() {
            return new FileIngredient(key, source);
          }
        });
  }


  /**
   * A set of files whose hash is the file paths, not their content, and
   * which is hashable when explicitly resolved.
   */
  @SuppressWarnings("synthetic-access")
  public FileSetIngredient fileset(Sources.Finder finder) {
    final Sources.Finder finderCopy = finder.clone();

    List<String> mainRootStrings = Lists.newArrayList();
    for (File f : finderCopy.mainRoots()) {
      mainRootStrings.add(f.getPath());
    }
    Collections.sort(mainRootStrings);
    List<String> testRootStrings = Lists.newArrayList();
    for (File f : finderCopy.testRoots()) {
      testRootStrings.add(f.getPath());
    }
    Collections.sort(testRootStrings);

    Escaper escaper = new Escaper(':', '\\', '[', ']', ',', ';', '"');

    StringBuilder keyBuilder = new StringBuilder();
    keyBuilder.append("fileset:");
    escaper.escape(finderCopy.suffixPattern().pattern(), keyBuilder);
    keyBuilder.append(";");
    escaper.escapeList(mainRootStrings, keyBuilder);
    keyBuilder.append(";");
    escaper.escapeList(testRootStrings, keyBuilder);

    final String key = keyBuilder.toString();
    return get(
        FileSetIngredient.class,
        key,
        new Provider<FileSetIngredient>() {
          public FileSetIngredient get() {
            return new FileSetIngredient(key, finderCopy);
          }
        });
  }

  public StringValue stringValue(final String s) {
    final String key = "str:" + s;
    return get(
        StringValue.class,
        key,
        new Provider<StringValue>() {
          public StringValue get() {
            return new StringValue(key, s);
          }
        });

  }

  public <T extends Options> OptionsIngredient<T> options(
      Class<T> optionsType, final T options) {
    final String key = Preconditions.checkNotNull(options.getKey());
    OptionsIngredient<?> ing = get(
        OptionsIngredient.class,
        key,
        new Provider<OptionsIngredient<T>>() {
          public OptionsIngredient<T> get() {
            return new OptionsIngredient<T>(key, options);
          }
        });
    return ing.asSuperType(optionsType);
  }

  public <T extends Serializable>
  SerializedObjectIngredient<T> serializedObject(
      File file, Class<T> contentType)
  throws IOException {
    return serializedObject(singletonSource(file), contentType);
  }

  public <T extends Serializable>
  SerializedObjectIngredient<T> serializedObject(
      final Source source, final Class<T> contentType) {
    final String key = "file:" + source.canonicalPath.getPath();
    SerializedObjectIngredient<?> ing = get(
        SerializedObjectIngredient.class, key,
        new Provider<SerializedObjectIngredient<T>>() {
          public SerializedObjectIngredient<T> get() {
            return new SerializedObjectIngredient<T>(key, source, contentType);
          }
        });
    return ing.asSuperType(contentType);
  }


  /** An ingredient backed by a file which is hashable when the file exists. */
  public static final class FileIngredient extends Ingredient {
    /** THe backing file. */
    public final Source source;

    private FileIngredient(String key, Source source) {
      super(key);
      this.source = source;
    }

    public Optional<Hash> hash() throws IOException {
      try {
        return Optional.of(Hash.hash(source));
      } catch (@SuppressWarnings("unused") FileNotFoundException ex) {
        return Optional.absent();
      }
    }
  }


  /**
   * A set of files whose hash is the file paths, not their content, and
   * which is hashable when explicitly resolved.
   */
  public final class FileSetIngredient extends Ingredient {
    private Sources.Finder finder;
    private Optional<ImmutableList<FileIngredient>> mainSources
        = Optional.absent();
    private Optional<ImmutableList<FileIngredient>> testSources
        = Optional.absent();
    private Optional<Hash> hash = Optional.absent();

    private FileSetIngredient(String key, Sources.Finder finder) {
      super(key);
      this.finder = Preconditions.checkNotNull(finder);
    }

    public synchronized Optional<Hash> hash() throws IOException {
      if (mainSources.isPresent() && testSources.isPresent()) {
        return Hash.hashAllHashables(
            ImmutableList.<FileIngredient>builder()
            .addAll(mainSources.get())
            .addAll(testSources.get()).build());
      } else {
        return Optional.absent();
      }
    }

    public synchronized Optional<ImmutableList<FileIngredient>> mainSources() {
      return mainSources;
    }

    public synchronized Optional<ImmutableList<FileIngredient>> testSources() {
      return testSources;
    }

    /** Scans the file-system to find matching files. */
    public synchronized void resolve(Log log) throws IOException {
      if (hash.isPresent()) {
        return;
      }
      Preconditions.checkNotNull(finder);
      Sources sources = finder.scan(log);
      ImmutableList<FileIngredient> mainSourceList = sortedSources(
          sources.mainFiles);
      ImmutableList<FileIngredient> testSourceList = sortedSources(
          sources.testFiles);
      this.mainSources = Optional.of(mainSourceList);
      this.testSources = Optional.of(testSourceList);
      this.hash = Optional.of(Hash.hashAllHashes(
          ImmutableList.of(
              Hash.hashSerializable(sources.mainFiles),
              Hash.hashSerializable(sources.testFiles))
          ));
      this.finder = null;
    }
  }

  public static final class OptionsIngredient<T extends Options>
  extends Ingredient {
    private final T options;

    OptionsIngredient(String key, T options) {
      super(key);
      this.options = clone(options);
    }

    public Optional<Hash> hash() throws NotSerializableException {
      return Optional.of(Hash.hashSerializable(options));
    }

    public String getId() { return options.getId(); }

    public T getOptions() {
      return clone(this.options);
    }

    public <ST extends Options>
    OptionsIngredient<ST> asSuperType(Class<ST> superType) {
      Preconditions.checkState(superType.isInstance(options));
      @SuppressWarnings("unchecked")
      OptionsIngredient<ST> casted = (OptionsIngredient<ST>) this;
      return casted;
    }

    private static <T extends Options> T clone(T options) {
      @SuppressWarnings("unchecked")
      Class<? extends T> optionsType =
          (Class<? extends T>) options.getClass();
      try {
        return optionsType.cast(options.clone());
      } catch (CloneNotSupportedException ex) {
        throw new IllegalArgumentException("Failed ot clone options", ex);
      }
    }
  }

  public static final class SerializedObjectIngredient<T extends Serializable>
  extends Ingredient {

    /** The file containing the serialized content. */
    public final Source source;
    /** The type of objects that can be stored in the file. */
    public final Class<T> type;
    /** The stored instance if any. */
    private Optional<T> instance = Optional.absent();

    SerializedObjectIngredient(String key, Source source, Class<T> type) {
      super(key);
      this.source = source;
      this.type = type;
    }

    public <ST extends Serializable>
    SerializedObjectIngredient<ST> asSuperType(Class<ST> superType) {
      Preconditions.checkState(superType.isAssignableFrom(type));
      @SuppressWarnings("unchecked")
      SerializedObjectIngredient<ST> casted =
          (SerializedObjectIngredient<ST>) this;
      return casted;
    }

    public Optional<Hash> hash() throws IOException {
      if (instance.isPresent()) {  // These had better be equivalent.
        return Optional.of(Hash.hashSerializable(instance.get()));
      }
      return Optional.absent();
    }

    public Optional<T> read() throws IOException {
      FileInputStream in;
      try {
        in = new FileInputStream(source.canonicalPath);
      } catch (@SuppressWarnings("unused") FileNotFoundException ex) {
        return Optional.absent();
      }
      try {
        ObjectInputStream objIn = new ObjectInputStream(in);
        Object deserialized;
        try {
          deserialized = objIn.readObject();
        } catch (ClassNotFoundException ex) {
          throw new IOException("Failed to deserialize", ex);
        }
        if (objIn.read() >= 0) {
          throw new IOException("Extraneous content in serialized object file");
        }
        instance = Optional.of(type.cast(deserialized));
        return instance;
      } finally {
        in.close();
      }
    }

    public Optional<T> getStoredObject() {
      return instance;
    }

    public void setStoredObject(T instance) {
      this.instance = Optional.of(instance);
    }

    public void write() throws IOException {
      Preconditions.checkState(instance.isPresent());
      source.canonicalPath.getParentFile().mkdirs();
      FileOutputStream out = new FileOutputStream(source.canonicalPath);
      try {
        ObjectOutputStream objOut = new ObjectOutputStream(out);
        try {
          objOut.writeObject(instance.get());
        } finally {
          objOut.close();
        }
      } finally {
        out.close();
      }
    }
  }

  ImmutableList<FileIngredient>
  sortedSources(Iterable<? extends Source> sources) {
    List<FileIngredient> hashedSources = Lists.newArrayList();
    for (Source s : sources) {
      hashedSources.add(file(s));
    }
    Collections.sort(
        hashedSources,
        new Comparator<FileIngredient>() {
          public int compare(FileIngredient a, FileIngredient b) {
            return a.source.canonicalPath.compareTo(b.source.canonicalPath);
          }
        });
    return ImmutableList.copyOf(hashedSources);
  }

  public static final class StringValue extends Ingredient {
    public final String value;
    StringValue(String key, String value) {
      super(key);
      this.value = value;
    }
    public Optional<Hash> hash() throws IOException {
      return Optional.of(Hash.hashString(value));
    }
  }

  static final class Escaper {
    final Pattern metachar;
    Escaper(char... metachars) {
      StringBuilder sb = new StringBuilder();
      sb.append("[\\\\");
      for (char c : metachars) {
        if (c == '-' || c == '\\' || c == ']' || c == '^') {
          sb.append('\\').append(c);
        }
      }
      sb.append(']');
      metachar = Pattern.compile(sb.toString());
    }

    void escape(CharSequence s, StringBuilder out) {
      Matcher m = metachar.matcher(s);
      int written = 0;
      int n = s.length();
      while (m.find()) {
        int start = m.start();
        int end = m.end();
        out.append(s, written, start).append('\\').append(m.group());
        written = end;
      }
      out.append(s, written, n);
    }

    void escapeList(Iterable<? extends String> strs, StringBuilder out) {
      out.append('[');
      boolean sawOne = false;
      for (String s : strs) {
        if (sawOne) {
          out.append(',');
        } else {
          sawOne = true;
        }
        escape(s, out);
      }
      out.append(']');
    }
  }
}
