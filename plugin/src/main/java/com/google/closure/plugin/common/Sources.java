package com.google.closure.plugin.common;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.DirectoryScanner;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

/** A group of source files split into test and production files. */
public final class Sources {
  /** Files that contribute source code to the artifact. */
  public final ImmutableList<Source> sources;

  /** An empty set of source files. */
  public static final Sources EMPTY = new Sources(
      ImmutableList.<Source>of());

  private Sources(
      Iterable<? extends Source> sources) {
    this.sources = ImmutableList.copyOf(sources);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Sources)) { return false; }
    Sources that = (Sources) o;
    return this.sources.equals(that.sources);
  }

  @Override
  public int hashCode() {
    return sources.hashCode();
  }

  /**
   * Scans the file-trees under the specified directories for files matching
   * the specified patterns.
   */
  public static Sources scan(
      Log log, DirectoryScannerSpec spec) throws IOException {
    String[] includesArray = spec.includes.toArray(new String[0]);
    String[] excludesArray = spec.excludes.toArray(new String[0]);

    Map<File, Source> found = Maps.newLinkedHashMap();
    for (TypedFile root : spec.roots) {
      if (!root.f.exists()) {
        log.debug("Skipping scan of non-extant root directory " + root.f);
        continue;
      }

      File canonRoot = root.f.getCanonicalFile();
      TypedFile typedCanonRoot = new TypedFile(canonRoot, root.ps);

      DirectoryScanner scanner = new DirectoryScanner();
      scanner.setBasedir(canonRoot);
      scanner.setIncludes(includesArray);
      scanner.setExcludes(excludesArray);
      scanner.addDefaultExcludes();
      scanner.setCaseSensitive(true);  // WTF MacOS
      scanner.setFollowSymlinks(true);

      scanner.scan();

      for (String relPath : scanner.getIncludedFiles()) {
        relPath = Files.simplifyPath(relPath);

        File file = new File(
            FilenameUtils.concat(canonRoot.getPath(), relPath));

        File canonFile = file.getCanonicalFile();
        TypedFile sourceRoot = typedCanonRoot;

        Source prev = found.get(canonFile);
        if (prev != null) {
          EnumSet<SourceFileProperty> combinedProps =
              EnumSet.noneOf(SourceFileProperty.class);
          combinedProps.addAll(sourceRoot.ps);
          combinedProps.retainAll(prev.root.ps);
          // We AND these together because of the following case-based
          // analysis over the properties.
          // TEST_ONLY -- if either file is not test only, then the combined is
          //     not test only.
          // LOAD_AS_NEEDED -- if either file is always loaded, the combined is
          //     always needed.

          sourceRoot = new TypedFile(sourceRoot.f, combinedProps);
        }

        File relFile = new File(relPath);

        Source source = new Source(canonFile, sourceRoot, relFile);
        found.put(canonFile, source);
      }
    }

    return new Sources(found.values());
  }

  /**
   * A source path.
   *
   * In the following directory structure,
   * <pre>
   * foo/
   *   bar &rarr; symlink to /baz
   * baz/
   *   boo.txt
   * </pre>
   * A traversal which follows symlinks from foo looking for *.txt files would
   * find a single Source:<pre>
   * {
   *   canonicalPath :"/baz/boo.txt",
   *   originalPath: "/foo/bar/boo.txt",
   *   root: "/foo",
   *   relativePath: "bar/boo.txt",
   * }</pre>
   */
  public static final class Source implements Comparable<Source>, Serializable {

    private static final long serialVersionUID = -6057344928125267557L;

    /** Maps sources to their canonical paths. */
    public static final Function<Source, File> GET_CANON_FILE
    = new Function<Source, File>() {
      @Override
      public File apply(Source s) {
        return s.canonicalPath;
      }
    };

    /** The canonical path to the source file on the local file system. */
    public final File canonicalPath;
    /** The canonical path to the source file root. */
    public final TypedFile root;
    /** The relative path from the root to the original path. */
    public final File relativePath;

    /**
     * @param canonicalPath The canonical path to the source file on the local
     *    file system.
     * @param root The canonical path to the source file root.
     * @param relativePath The relative path from the root to the original path.
     */
    public Source (
        File canonicalPath,
        TypedFile root,
        File relativePath) {
      this.canonicalPath = canonicalPath;
      this.root = root;
      this.relativePath = relativePath;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Source)) { return false; }
      Source that = (Source) o;
      return this.canonicalPath.equals(that.canonicalPath)
          && this.root.equals(that.root);
    }

    @Override
    public int hashCode() {
      return canonicalPath.hashCode() + 31 * root.hashCode();
    }

    @Override
    public int compareTo(Source that) {
      return this.canonicalPath.compareTo(that.canonicalPath);
    }

    @Override
    public String toString() {
      return "{canonicalPath=`" + canonicalPath
          + "`, root=`" + root
          + "`, relativePath=`" + relativePath + "`}";
    }

    /**
     * Resolves the given path against this source file.
     */
    public Source resolve(String uriPath)
        throws IOException, URISyntaxException {
      if ("".equals(uriPath)) {
        return this;  // Or the parent directory.
      }

      String[] parts = uriPath.split("/+");

      File relFile;  // Relative path to the resolved file.
      if ("".equals(parts[0])) {  // uriPath.startsWith("/")
        relFile = null;
      } else {
        relFile = relativePath.getParentFile();
      }

      File absFile;  // Absolute path to the resolved file.
      if (relFile != null) {
        absFile = new File(FilenameUtils.concat(
            this.root.f.getPath(), relFile.getPath()));
      } else {
        absFile = this.root.f;
      }

      boolean sawPathParts = false;
      for (String part : parts) {
        if ("".equals(part)) { continue; }
        relFile = new File(relFile, part);
        absFile = new File(absFile, part);
        sawPathParts = true;
      }
      if (relFile == null || !sawPathParts) {
        throw new URISyntaxException(uriPath, "No path parts");
      }

      // We need to normalize to get rid of .. and . path segments.
      absFile = absFile.getCanonicalFile();
      relFile = new File(FilenameUtils.normalizeNoEndSeparator(
          relFile.getPath()));

      return new Source(absFile, root, relFile);
    }

    /**
     * The file, but with the given suffix appended.
     */
    public Source suffix(String suffix) {
      return new Source(
          new File(canonicalPath.getParentFile(),
                   canonicalPath.getName() + suffix),
          root,
          new File(relativePath.getParentFile(),
                   relativePath.getName() + suffix));
    }

    /**
     * The same relativePath but with the given root.
     */
    public Source reroot(TypedFile newRoot) throws IOException {
      File rerooted = new File(FilenameUtils.concat(
          newRoot.f.getPath(), this.relativePath.getPath()));
      return new Source(
          rerooted.getCanonicalFile(),
          newRoot,
          this.relativePath);
    }
  }


  static URI uriOfPath(File f) throws URISyntaxException {
    String path = f.getPath();
    path = path.replace("%", "%25");
    if (File.separatorChar != '/') {
      assert(File.separatorChar != '%');
      path = path.replace("/", "%2f").replace(File.separatorChar, '/');
    }
    return new URI(path);
  }

  static String prettyPatternQuote(String s) {
    int n = s.length();
    if (n != 0) {
      // If it's only dot and letters then we can make a more readable
      // pattern than \Q which is confusing in log output.
      boolean onlyDotAndLetters = true;
      for (int i = 0; i < n; ++i) {
        char ch = s.charAt(i);
        if (!(ch == '.' || (ch < 0x80 && Character.isLetterOrDigit(ch)))) {
          onlyDotAndLetters = false;
          break;
        }
      }
      if (onlyDotAndLetters) {
        return s.replace(".", "\\.");
      }
    }
    return Pattern.quote(s);
  }
}
