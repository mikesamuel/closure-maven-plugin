package com.google.closure.plugin.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URI;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.plan.Hash;
import com.google.closure.plugin.plan.Hashable;
import com.google.closure.plugin.plan.Ingredient;
import com.google.closure.plugin.plan.KeyedSerializable;
import com.google.closure.plugin.plan.PlanKey;

/**
 * Pools ingredients based on key.
 */
public final class Ingredients {

  private final Cache<PlanKey, Ingredient> ingredients =
      CacheBuilder.newBuilder()
      // TODO: is this right?
      .weakValues()
      .build();

  /** A directory to store cached results. */
  private final File cacheDir;

  /**
   * @param outputDir the target directory.
   */
  public Ingredients(File outputDir) {
    this.cacheDir = new File(outputDir, ".closure-comp-cache");
  }

  /** A directory to store cached results. */
  public File getCacheDir() {
    return cacheDir;
  }

  /**
   * Lazily allocate an ingredient with the given key based on a spec.
   */
  public <T extends Ingredient>
  T get(Class<T> type, PlanKey key, final Supplier<? extends T> maker) {
    Ingredient got;
    try {
      got = ingredients.get(key, new Callable<T>() {
        @Override
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

  private static Source singletonSource(
      File file, Set<SourceFileProperty> props) throws IOException {
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
    return new Sources.Source(
        canonFile, new TypedFile(rf.root, props), relFile);
  }

  /** An ingredient backed by a file which is hashable when the file exists. */
  public FileIngredient file(File file) throws IOException {
    return file(singletonSource(file, ImmutableSet.<SourceFileProperty>of()));
  }

  /** An ingredient backed by a file which is hashable when the file exists. */
  public FileIngredient file(TypedFile file) throws IOException {
    return file(singletonSource(file.f, file.ps));
  }

  /** An ingredient backed by a file which is hashable when the file exists. */
  public FileIngredient file(final Source source) {
    final PlanKey key = PlanKey.builder("file")
        .addString(source.canonicalPath.getPath())
        .build();
    return get(
        FileIngredient.class,
        key,
        new Supplier<FileIngredient>() {
          @SuppressWarnings("synthetic-access")
          @Override
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
  public DirScanFileSetIngredient fileset(final DirectoryScannerSpec spec) {
    PlanKey.Builder keyBuilder = PlanKey.builder("fileset");
    keyBuilder.addStrings(spec.includes);
    keyBuilder.addStrings(spec.excludes);

    List<String> strs = Lists.newArrayList();
    for (TypedFile root : spec.roots) {
      strs.clear();
      strs.add(root.f.getPath());
      for (SourceFileProperty p : root.ps) {
        strs.add(p.name());
      }
      keyBuilder.addStrings(strs);
    }

    final PlanKey key = keyBuilder.build();

    return get(
        DirScanFileSetIngredient.class,
        key,
        new Supplier<DirScanFileSetIngredient>() {
          @Override
          public DirScanFileSetIngredient get() {
            return new DirScanFileSetIngredient(key, spec);
          }
        });
  }

  /**
   * A file-set that has a name but whose content is derived from some
   * computation that is not itself hashable.
   */
  public SettableFileSetIngredient namedFileSet(String name) {
    final PlanKey key = PlanKey.builder("named-files").addString(name).build();
    return get(
        SettableFileSetIngredient.class,
        key,
        new Supplier<SettableFileSetIngredient>() {
          @Override
          public SettableFileSetIngredient get() {
            return new SettableFileSetIngredient(key);
          }
        });
  }


  /** An ingredient that represents a fixed string. */
  public StringValue stringValue(final String s) {
    final PlanKey key = PlanKey.builder("str").addString(s).build();
    return get(
        StringValue.class,
        key,
        new Supplier<StringValue>() {
          @Override
          public StringValue get() {
            return new StringValue(key, s);
          }
        });

  }

  /**
   * An ingredient that represents a path, and hashes to a hash of that
   * path, not the content of the file referred to by that path.
   */
  public PathValue pathValue(final File f) {
    final PlanKey key = PlanKey.builder("path").addString(f.getPath()).build();
    return get(
        PathValue.class,
        key,
        new Supplier<PathValue>() {
          @Override
          public PathValue get() {
            return new PathValue(key, f);
          }
        });
  }

  /**
   * An ingredient that represents a path, and hashes to a hash of that
   * path, not the content of the file referred to by that path.
   */
  public UriValue uriValue(final URI uri) {
    final PlanKey key = PlanKey.builder("uri")
        .addString(uri.toASCIIString())
        .build();
    return get(
        UriValue.class,
        key,
        new Supplier<UriValue>() {
          @Override
          public UriValue get() {
            return new UriValue(key, uri);
          }
        });
  }

  /**
   * Specifies how the compiler should interpret a group of source files and
   * where to look for those source files.
   */
  public <T extends KeyedSerializable> HashedInMemory<T> hashedInMemory(
      Class<T> valueType, final T value) {
    final PlanKey key = Preconditions.checkNotNull(value.getKey());
    HashedInMemory<?> ing = get(
        HashedInMemory.class,
        key,
        new Supplier<HashedInMemory<T>>() {
          @Override
          public HashedInMemory<T> get() {
            return new HashedInMemory<>(key, value);
          }
        });
    return ing.asSuperType(valueType);
  }

  /**
   * An ingredient back by a file dedicated to hold a serialized object of
   * a specific type.  Reading and writing must be done explicitly and the
   * hash is of the version in memory.
   */
  public <T extends Serializable>
  SerializedObjectIngredient<T> serializedObject(
      String baseName, final Class<T> contentType)
  throws IOException {
    final File canonFile = new File(cacheDir, baseName).getCanonicalFile();
    final PlanKey key = PlanKey.builder("serialized-object")
        .addString(baseName)
        .build();
    SerializedObjectIngredient<?> ing = get(
        SerializedObjectIngredient.class, key,
        new Supplier<SerializedObjectIngredient<T>>() {
          @Override
          public SerializedObjectIngredient<T> get() {
            return new SerializedObjectIngredient<>(
                key, canonFile, contentType);
          }
        });
    return ing.asSuperType(contentType);
  }

  /**
   * Group a bunch of related ingredients together.
   */
  public <I extends Ingredient> Bundle<I> bundle(Iterable<? extends I> ings) {
    final ImmutableList<I> ingList = ImmutableList.copyOf(ings);
    PlanKey.Builder keyBuilder = PlanKey.builder("bundle");
    keyBuilder.addInp(ingList);
    final PlanKey key = keyBuilder.build();

    Bundle<?> bundle = get(
        Bundle.class, key,
        new Supplier<Bundle<I>>() {
          @Override
          public Bundle<I> get() {
            return new Bundle<>(key, ingList);
          }
        });

    // Succeeds when key prefix spaces are disjoint for different types of
    // ingredients.
    Preconditions.checkState(ingList.equals(bundle.ings));

    // Sound when the above precondition passes.
    @SuppressWarnings("unchecked")
    Bundle<I> typedBundle = (Bundle<I>) bundle;

    return typedBundle;
  }


  /** An ingredient backed by a file which is hashable when the file exists. */
  public static final class FileIngredient extends Ingredient {
    /** Gets {@link #source}. */
    public static final Function<FileIngredient, Source> GET_SOURCE =
        new Function<FileIngredient, Source>() {
          @Override
          public Source apply(FileIngredient ing) {
            return ing.source;
          }
        };

    /** The backing file. */
    public final Source source;
    private Optional<Hash> hash = Optional.absent();

    private FileIngredient(PlanKey key, Source source) {
      super(key);
      this.source = source;
    }

    @Override
    public Optional<Hash> hash() throws IOException {
      Optional<Hash> lhash;
      synchronized (this) {
        lhash = this.hash;
      }
      if (! lhash.isPresent()) {
        try {
          lhash = Optional.of(Hash.hash(source));
        } catch (@SuppressWarnings("unused") FileNotFoundException ex) {
          // If the file is not found then absent is correct.
        }
        synchronized (this) {
          if (!this.hash.isPresent()) {
            this.hash = lhash;
          }
          lhash = this.hash;
        }
      }
      return lhash;
    }
  }

  /** A group of files that need not be known at construct time. */
  public abstract class FileSetIngredient extends Ingredient {
    private Optional<ImmutableList<FileIngredient>> sources = Optional.absent();
    private Optional<Hash> hash = Optional.absent();
    private MojoExecutionException problem;

    FileSetIngredient(PlanKey key) {
      super(key);
    }

    @Override
    public final synchronized Optional<Hash> hash() throws IOException {
      if (sources.isPresent()) {
        return Hash.hashAllHashables(sources.get());
      } else {
        return Optional.absent();
      }
    }

    /** Source files that should contribute to the artifact. */
    public synchronized ImmutableList<FileIngredient> sources()
    throws MojoExecutionException {
      if (sources.isPresent()) {
        return sources.get();
      } else {
        throw getProblem();
      }
    }

    /** The reason the sources are not available. */
    protected final MojoExecutionException getProblem() {
      MojoExecutionException mee = problem;
      return (mee == null)
          ? new MojoExecutionException(key + " never set")
          : mee;
    }

    /**
     * May be called if inputs to {@link #setSources} are unavailable due to an
     * exceptional condition.
     */
    protected void setProblem(Throwable th) {
      if (th instanceof MojoExecutionException) {
        problem = (MojoExecutionException) th;
      } else {
        problem = new MojoExecutionException("Could not determine " + key, th);
      }
    }

    protected final
    void setSources(Iterable<? extends FileIngredient> newSources)
    throws IOException {
      ImmutableList<FileIngredient> newSourceList =
          ImmutableList.copyOf(newSources);
      Hash hashOfFiles = Hash.hashAllHashables(newSourceList).get();

      Optional<ImmutableList<FileIngredient>> newSourceListOpt =
          Optional.of(newSourceList);
      Optional<Hash> hashOfFilesOpt = Optional.of(hashOfFiles);

      synchronized (this) {
        Preconditions.checkState(!sources.isPresent());
        this.sources = newSourceListOpt;
        this.hash = hashOfFilesOpt;
      }
    }

    protected final
    void setMainSources(Iterable<? extends FileIngredient> sources) {
      Optional<ImmutableList<FileIngredient>> newSources = Optional.of(
          ImmutableList.copyOf(sources));
      synchronized (this) {
        Preconditions.checkState(!this.sources.isPresent());
        this.sources = newSources;
      }
    }

    /**
     * True iff the sources have been resolved to a concrete list
     * of extant files.
     */
    public synchronized boolean isResolved() {
      return hash.isPresent();
    }
  }

  /**
   * A file-set that can be explicitly set.   It's key does not
   * depend upon it's specification, so key uniqueness is the responsibility of
   * its creator.
   */
  public final class SettableFileSetIngredient extends FileSetIngredient {

    SettableFileSetIngredient(PlanKey key) {
      super(key);
    }

    /**
     * May be called once to specify the files in the set, as the file set
     * transitions from being used as an input to being used as an output.
     */
    public void setFiles(Iterable<? extends FileIngredient> newSources)
    throws IOException {
      this.setSources(newSources);
    }

    @Override
    public void setProblem(Throwable th) {
      super.setProblem(th);
    }
  }

  /**
   * A set of files whose hash is the file paths, not their content, and
   * which is hashable when explicitly resolved.
   */
  public final class DirScanFileSetIngredient
  extends FileSetIngredient implements Hashable.AutoResolvable {
    private final DirectoryScannerSpec spec;

    private DirScanFileSetIngredient(PlanKey key, DirectoryScannerSpec spec) {
      super(key);
      this.spec = Preconditions.checkNotNull(spec);
    }

    /** The specification for the set of files to scan. */
    public DirectoryScannerSpec spec() {
      return spec;
    }

    /** Scans the file-system to find matching files. */
    @Override
    public synchronized void resolve(Log log) throws IOException {
      if (isResolved()) {
        return;
      }
      try {
        Sources sources = Sources.scan(log, spec);
        log.debug(
            "Directory scan found " + sources.sources.size()
            + " sources for " + spec.toString());

        ImmutableList.Builder<FileIngredient> fileIngredientList =
            ImmutableList.builder();
        for (Source source : sources.sources) {
          fileIngredientList.add(Ingredients.this.file(source));
        }

        this.setSources(fileIngredientList.build());
      } catch (IOException ex) {
        setProblem(new MojoExecutionException(
            "Resolution of " + key + " failed", ex));
        throw ex;
      }
    }
  }

  /**
   * Specifies how the compiler should interpret a group of source files and
   * where to look for those source files.
   */
  public static final class HashedInMemory<T extends Serializable>
  extends Ingredient {
    private final T value;

    HashedInMemory(PlanKey key, T value) {
      super(key);
      this.value = value;
    }

    @Override
    public Optional<Hash> hash() throws NotSerializableException {
      return Optional.of(Hash.hashSerializable(value));
    }

    /**
     * A shallow copy of options since options are often mutable objects.
     */
    public T getValue() {
      return this.value;
    }

    /**
     * Runtime recast that the underlying options value has the given type.
     */
    public <ST extends Serializable>
    HashedInMemory<ST> asSuperType(Class<ST> superType) {
      Preconditions.checkState(superType.isInstance(value));
      @SuppressWarnings("unchecked")
      HashedInMemory<ST> casted = (HashedInMemory<ST>) this;
      return casted;
    }
  }


  /**
   * An ingredient back by a file dedicated to hold a serialized object of
   * a specific type.  Reading and writing must be done explicitly and the
   * hash is of the version in memory.
   */
  public static final class SerializedObjectIngredient<T extends Serializable>
  extends Ingredient implements Hashable.AutoResolvable {

    /** The file containing the serialized content. */
    public final File file;
    /** The type of objects that can be stored in the file. */
    public final Class<T> type;
    /** The stored instance if any. */
    private Optional<T> instance = Optional.absent();

    SerializedObjectIngredient(PlanKey key, File file, Class<T> type) {
      super(key);
      this.file = file;
      this.type = type;
    }

    /** Runtime recast of the underlying object. */
    public <ST extends Serializable>
    SerializedObjectIngredient<ST> asSuperType(Class<ST> superType) {
      Preconditions.checkState(superType.isAssignableFrom(type));
      @SuppressWarnings("unchecked")
      SerializedObjectIngredient<ST> casted =
          (SerializedObjectIngredient<ST>) this;
      return casted;
    }

    @Override
    public Optional<Hash> hash() throws IOException {
      if (instance.isPresent()) {  // These had better be equivalent.
        return Optional.of(Hash.hashSerializable(instance.get()));
      }
      return Optional.absent();
    }

    /** Read the content of the file into memory. */
    public void read() throws IOException {
      FileInputStream in;
      try {
        in = new FileInputStream(file);
      } catch (@SuppressWarnings("unused") FileNotFoundException ex) {
        return;  // This is best effort.
      }
      try {
        Object deserialized;
        try (ObjectInputStream objIn = new ObjectInputStream(in)) {
          try {
            deserialized = objIn.readObject();
          } catch (ClassNotFoundException ex) {
            throw new IOException("Failed to deserialize", ex);
          }
          if (objIn.read() >= 0) {
            throw new IOException(
                "Extraneous content in serialized object file");
          }
        }
        instance = Optional.of(type.cast(deserialized));
      } finally {
        in.close();
      }
    }

    /**
     * The in-memory version of the object if it has been {@link #read read}
     * or {@link #setStoredObject stored}.
     */
    public Optional<T> getStoredObject() {
      return instance;
    }

    /**
     * Sets the in-memory instance.
     */
    public void setStoredObject(T instance) {
      this.instance = Optional.of(instance);
    }

    /**
     * Writes the in-memory instance to the {@link #file persisting file}.
     */
    public void write() throws IOException {
      Preconditions.checkState(instance.isPresent());
      file.getParentFile().mkdirs();
      try (FileOutputStream out = new FileOutputStream(file)) {
        try (ObjectOutputStream objOut = new ObjectOutputStream(out)) {
          objOut.writeObject(instance.get());
        }
      }
    }

    @Override
    public void resolve(Log log) throws IOException {
      read();
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
          @Override
          public int compare(FileIngredient a, FileIngredient b) {
            return a.source.canonicalPath.compareTo(b.source.canonicalPath);
          }
        });
    return ImmutableList.copyOf(hashedSources);
  }

  /** An ingredient that represents a fixed string. */
  public static final class StringValue extends Ingredient {
    /** The fixed string value. */
    public final String value;
    StringValue(PlanKey key, String value) {
      super(key);
      this.value = value;
    }
    @Override
    public Optional<Hash> hash() throws IOException {
      return Optional.of(Hash.hashString(value));
    }

    @Override
    public String toString() {
      return "{StringValue " + value + "}";
    }
  }

  /**
   * An ingredient that represents a path, and hashes to a hash of that
   * path, not the content of the file referred to by that path.
   */
  public static final class PathValue extends Ingredient {
    /** The fixed path value. */
    public final File value;

    PathValue(PlanKey key, File value) {
      super(key);
      this.value = value;
    }
    @Override
    public Optional<Hash> hash() throws IOException {
      return Optional.of(Hash.hashString(value.getPath()));
    }

    @Override
    public String toString() {
      return "{PathValue " + value.getPath() + "}";
    }
  }

  /**
   * An ingredient that represents a URI, and hashes to a hash of that
   * URI's text, not the content referred to by that URI.
   */
  public static final class UriValue extends Ingredient {
    /** The fixed URI value. */
    public final URI value;

    UriValue(PlanKey key, URI value) {
      super(key);
      this.value = value;
    }
    @Override
    public Optional<Hash> hash() throws IOException {
      return Optional.of(Hash.hashString(value.toString()));
    }

    @Override
    public String toString() {
      return "{UriValue " + value.toString() + "}";
    }
  }

  /**
   * A bundle of ingredients.
   */
  public static final class Bundle<I extends Ingredient> extends Ingredient {
    /** The constituent ingredients. */
    public final ImmutableList<I> ings;

    Bundle(PlanKey key, Iterable<? extends I> ings) {
      super(key);
      this.ings = ImmutableList.copyOf(ings);
    }

    /** Type coercion. */
    public <J extends Ingredient> Bundle<J> asSuperType(
        Function<? super Ingredient, ? extends J> typeCheckingIdentityFn) {
      for (Ingredient ing : ings) {
        J typedIng = typeCheckingIdentityFn.apply(ing);
        Preconditions.checkState(ing == typedIng);
      }
      @SuppressWarnings("unchecked")
      // Sound when typeCheckingIdentityFn is sound.
      Bundle<J> typedBundle = (Bundle<J>) this;
      return typedBundle;
    }

    /** Type coercion. */
    public <J extends Ingredient> Bundle<J> asSuperType(final Class<J> typ) {
      return asSuperType(new Function<Ingredient, J>() {
        @Override
        public J apply(Ingredient ing) {
          return typ.cast(ing);
        }
      });
    }

    @Override
    public Optional<Hash> hash() throws IOException {
      ImmutableList.Builder<Hash> hashes = ImmutableList.builder();
      for (Ingredient ing : ings) {
        Optional<Hash> h = ing.hash();
        if (h.isPresent()) {
          hashes.add(h.get());
        } else {
          return Optional.absent();
        }
      }
      return Optional.of(Hash.hashAllHashes(hashes.build()));
    }
  }
}
