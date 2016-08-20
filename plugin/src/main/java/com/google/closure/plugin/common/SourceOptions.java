package com.google.closure.plugin.common;

import java.io.File;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * Options that specify how to find sources.
 */
@SuppressWarnings("serial")
public abstract class SourceOptions extends Options {
  /**
   * Source file roots.
   */
  public SourceRootBuilder[] source;

  /**
   * Test file roots.
   */
  public SourceRootBuilder[] testSource;

  /**
   * Path patterns to include specified as ANT-directory-scanner-style patterns
   * like <code>**<nobr></nobr>/*.ext</code>.
   */
  public String[] include;

  /**
   * Path patterns to exclude specified as ANT-directory-scanner-style patterns
   * like <code>**<nobr></nobr>/*.ext</code>.
   */
  public String[] exclude;


  /** Snapshots. */
  public final DirectoryScannerSpec toDirectoryScannerSpec(
      File defaultMainRoot, File defaultTestRoot,
      GenfilesDirs genfilesDirs) {

    ImmutableList.Builder<TypedFile> allRoots = ImmutableList.builder();
    if (source != null && source.length != 0) {
      for (SourceRootBuilder oneSource : source) {
        allRoots.add(oneSource.build());
      }
    } else {
      allRoots.add(new TypedFile(defaultMainRoot));
    }
    if (testSource != null && testSource.length != 0) {
      for (SourceRootBuilder oneSource : testSource) {
        allRoots.add(oneSource.build(SourceFileProperty.TEST_ONLY));
      }
    } else {
      allRoots.add(new TypedFile(
          defaultTestRoot, SourceFileProperty.TEST_ONLY));
    }

    ImmutableList<String> sourceExtensions = sourceExtensions();
    for (String ext : sourceExtensions) {
      Preconditions.checkState(!ext.startsWith("."));
      for (EnumSet<SourceFileProperty> subset
           : subsetsOf(EnumSet.allOf(SourceFileProperty.class))) {
        allRoots.add(
            new TypedFile(
                genfilesDirs.getGeneratedSourceDirectory(ext, subset),
                subset));
      }
    }

    ImmutableList.Builder<String> allIncludes = ImmutableList.builder();
    if (include != null && include.length != 0) {
      allIncludes.add(include);
    } else {
      for (String ext : sourceExtensions) {
        allIncludes.add("**/*." + ext);
      }
    }

    ImmutableList<String> allExcludes =  // ANT defaults added later.
        exclude != null
        ? ImmutableList.copyOf(exclude)
            : ImmutableList.<String>of();

    return new DirectoryScannerSpec(
        allRoots.build(), allIncludes.build(), allExcludes);
  }

  /**
   * Source extensions used to compute default includes.
   * For example, a source extension of {@code "js"} implies a default include
   * of <code>**<nobr></nobr>/*.js</code>.
   */
  protected abstract ImmutableList<String> sourceExtensions();


  /**
   * A plexus-configurable source root.
   */
  public static final class SourceRootBuilder {
    private File root;
    private final EnumSet<SourceFileProperty> props =
        EnumSet.noneOf(SourceFileProperty.class);

    /** The default string form just specifies the file. */
    public void set(File f) {
      setRoot(f);
    }

    /** The root directory under which to search. */
    public void setRoot(File f) {
      if (f.exists() && !f.isDirectory()) {
        throw new IllegalArgumentException(f + " is not a directory");
      }
      this.root = f;
    }

    /** Adds the file properties to those already specified. */
    public void setFileProperty(SourceFileProperty... properties) {
      props.addAll(Arrays.asList(properties));
    }

    /**
     * Whether the source files found under this root are only used when needed
     * to satisfy dependencies.
     */
    public void setLoadAsNeeded(boolean b) {
      setProperty(b, SourceFileProperty.LOAD_AS_NEEDED);
    }

    /**
     * Whether the source files found under this root are used only in testing.
     */
    public void setTestOnly(boolean b) {
      setProperty(b, SourceFileProperty.TEST_ONLY);
    }

    private void setProperty(boolean b, SourceFileProperty p) {
      if (b) {
        props.add(p);
      } else {
        props.remove(p);
      }
    }

    /** An immutable snapshot of the current state. */
    public TypedFile build(SourceFileProperty... implied) {
      if (root == null) {
        throw new IllegalArgumentException(
            "Must specify a root directory");
      }
      EnumSet<SourceFileProperty> allProps = EnumSet.copyOf(this.props);
      allProps.addAll(Arrays.asList(implied));
      return new TypedFile(root, allProps);
    }
  }


  private static <T extends Enum<T>>
  Iterable<EnumSet<T>> subsetsOf(EnumSet<T> set) {
    if (set.isEmpty()) {
      return ImmutableList.of(EnumSet.copyOf(set));
    }
    T first = set.iterator().next();
    // Sound because all enum instances allowed in EnumSet are final.
    @SuppressWarnings("unchecked")
    final Class<T> typ = (Class<T>) first.getClass();
    // Sound because typ is a reference type so T[] is an Object[].
    @SuppressWarnings("unchecked")
    final T[] els = (T[]) Array.newInstance(typ, set.size());

    set.toArray(els);

    Preconditions.checkState(els.length <= 63);  // Assume no overflow.
    final long n = 1L << els.length;

    return new Iterable<EnumSet<T>>() {

      @Override
      public Iterator<EnumSet<T>> iterator() {
        return new Iterator<EnumSet<T>>() {
          private long idx = 0;

          @Override
          public boolean hasNext() {
            return idx != n;  // Handles underflow of n
          }

          @Override
          public EnumSet<T> next() {
            if (!hasNext()) { throw new NoSuchElementException(); }
            EnumSet<T> es = EnumSet.noneOf(typ);
            for (int i = 0; i < els.length; ++i) {
              if ((idx & (1L << i)) != 0) {
                es.add(els[i]);
              }
            }
            ++idx;
            return es;
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }
}
