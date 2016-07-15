package com.google.common.html.plugin;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

/** A group of source files split into test and production files. */
public final class Sources {
  /** Files that contribute source code to the artifact. */
  public final ImmutableList<Source> mainFiles;
  /** Files that are used in testing the artifact. */
  public final ImmutableList<Source> testFiles;

  /** An empty set of source files. */
  public static final Sources EMPTY = new Sources(
      ImmutableList.<Source>of(), ImmutableList.<Source>of());

  private Sources(
      Iterable<? extends Source> mainFiles,
      Iterable<? extends Source> testFiles) {
    this.mainFiles = ImmutableList.copyOf(mainFiles);
    this.testFiles = ImmutableList.copyOf(testFiles);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Sources)) { return false; }
    Sources that = (Sources) o;
    return this.mainFiles.equals(that.mainFiles)
        && this.testFiles.equals(that.testFiles);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(mainFiles, testFiles);
  }


  /**
   * A source path.
   *
   * In the following directory structure,
   * <pre>
   * foo/
   *   bar -> symlink to /baz
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

    /** The canonical path to the source file on the local file system. */
    public final File canonicalPath;
    /** The canonical path to the source file root. */
    public final File root;
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
        File root,
        File relativePath) {
      this.canonicalPath = canonicalPath;
      this.root = root;
      this.relativePath = relativePath;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Source)) { return false; }
      return this.canonicalPath.equals(((Source) o).canonicalPath);
    }

    @Override
    public int hashCode() {
      return canonicalPath.hashCode();
    }

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
            this.root.getPath(), relFile.getPath()));
      } else {
        absFile = this.root;
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
    public Source reroot(File newRoot) throws IOException {
      File rerooted = new File(FilenameUtils.concat(
          newRoot.getPath(), this.relativePath.getPath()));
      return new Source(
          rerooted.getCanonicalFile(),
          newRoot,
          this.relativePath);
    }
  }


  /**
   * Scans a search path for source files.
   */
  public static final class Finder {
    private final Pattern suffixPattern;
    private final ImmutableList.Builder<File> mainRoots =
        ImmutableList.builder();
    private final ImmutableList.Builder<File> testRoots =
        ImmutableList.builder();

    /**
     * @param suffixes file suffixes to search for like ".js".
     */
    public Finder(String... suffixes) {
      ImmutableList.Builder<String> suffixPatterns = ImmutableList.builder();
      for (String suffix : suffixes) {
        suffixPatterns.add(prettyPatternQuote(suffix));
      }
      this.suffixPattern = Pattern.compile(
          "(?:" + Joiner.on("|").join(suffixPatterns.build()) + ")\\z");
    }

    Finder(Pattern suffixPattern) {
      this.suffixPattern = suffixPattern;
    }

    @Override
    public Finder clone() {
      return new Finder(suffixPattern)
          .mainRoots(this.mainRoots.build())
          .testRoots(this.testRoots.build());
    }

    /**
     * The pattern that matches suffixes of the files we're looking for.
     */
    public Pattern suffixPattern() {
      return suffixPattern;
    }

    /**
     * Search path elements containing production files.
     */
    public ImmutableList<File> mainRoots() {
      return mainRoots.build();
    }

    /**
     * Adds to {@link #mainRoots()}.
     */
    public Finder mainRoots(File... roots) {
      return mainRoots(Arrays.asList(roots));
    }

    /**
     * Adds to {@link #mainRoots()}.
     */
    public Finder mainRoots(Iterable<? extends File> roots) {
      mainRoots.addAll(roots);
      return this;
    }

    /**
     * Search path elements containing test files.
     */
    public ImmutableList<File> testRoots() {
      return testRoots.build();
    }

    /**
     * Adds to {@link #testRoots()}.
     */
    public Finder testRoots(File... roots) {
      return testRoots(Arrays.asList(roots));
    }

    /**
     * Adds to {@link #testRoots()}.
     */
    public Finder testRoots(Iterable<? extends File> roots) {
      testRoots.addAll(roots);
      return this;
    }

    /**
     * Walk the file trees under the source roots looking for files matching
     * the suffix pattern.
     */
    @SuppressWarnings("synthetic-access")
    public Sources scan(Log log) throws IOException {
      // Using treeset to get a reliable file order makes later build stages
      // less volatile.
      final Set<Source> mainFiles = Sets.newTreeSet();
      final Set<Source> testFiles = Sets.newTreeSet();

      for (File root : mainRoots.build()) {
        if (!root.exists()) {
          log.debug("Skipping missing directory: " + root);
          continue;
        }
        log.debug(
            "Scanning " + root + " for files matching " + this.suffixPattern);

        find(root, suffixPattern, mainFiles);
      }

      for (File root : testRoots.build()) {
        if (!root.exists()) {
          log.debug("Skipping missing directory: " + root);
          continue;
        }

        find(root, suffixPattern, testFiles);
      }

      return new Sources(mainFiles, testFiles);
    }
  }

  private static void find(
      final File root,
      final Pattern basenamePattern, final Collection<? super Source> out)
  throws IOException {
    final Path rootPath = root.toPath();
    Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
      private File relPath = null;

      @Override
      public FileVisitResult preVisitDirectory(
          Path p, BasicFileAttributes attrs) {
        if (relPath != null || !p.equals(rootPath)) {
          relPath = new File(relPath, p.getFileName().toString());
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(
          Path p, @Nullable IOException ex) {
        if (relPath != null) {
          relPath = relPath.getParentFile();
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path p, BasicFileAttributes attrs)
      throws IOException {
        String basename = p.getFileName().toString();
        if (basenamePattern.matcher(basename).find()) {
          Source source = new Source(
              p.toFile().getCanonicalFile(),
              root,
              new File(relPath, basename));
          out.add(source);
        }
        return FileVisitResult.CONTINUE;
      }
    });
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
