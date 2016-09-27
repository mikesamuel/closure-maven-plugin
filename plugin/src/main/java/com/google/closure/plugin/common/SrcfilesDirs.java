package com.google.closure.plugin.common;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.EnumSet;

import org.apache.maven.project.MavenProject;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/** Source directories by language. */
public final class SrcfilesDirs implements Serializable {
  private static final long serialVersionUID = -4140910888130420239L;

  /** ${basedir}/src */
  public final File srcDir;
  /** ${basedir}/dep */
  public final File depDir;
  /** The java production source roots. */
  public final ImmutableList<File> javaMainRoots;
  /** The java test source roots. */
  public final ImmutableList<File> javaTestRoots;

  /** */
  public SrcfilesDirs(MavenProject project) throws IOException {
    this(project.getBasedir(), fileList(project.getCompileSourceRoots()),
        fileList(project.getTestCompileSourceRoots()));
  }

  /** */
  public SrcfilesDirs(
      File baseDir,
      Iterable<? extends File> compileSourceRoots,
      Iterable<? extends File> testCompileSourceRoots) {
    this.srcDir = new File(baseDir, "src");
    this.depDir = new File(baseDir, "dep");
    this.javaMainRoots = ImmutableList.copyOf(compileSourceRoots);
    this.javaTestRoots = ImmutableList.copyOf(testCompileSourceRoots);
  }


  private static ImmutableList<File> fileList(Iterable<? extends String> paths)
  throws IOException {
    // TODO: Do we need to resolve against basedir?
    ImmutableList.Builder<File> out = ImmutableList.builder();
    for (String path : paths) {
      File root = new File(path).getCanonicalFile();
      out.add(root);
    }
    return out.build();
  }

  /**
   * The default project source directory for files with the given extension and
   * properties.
   * <p>
   * See {@link SourceOptions#source} and {@link SourceOptions#testSource}
   * which override the default when specified.
   */
  public TypedFile getDefaultProjectSourceDirectory(
      FileExt extension, SourceFileProperty... props) {
    EnumSet<SourceFileProperty> propSet =
        EnumSet.noneOf(SourceFileProperty.class);
    for (SourceFileProperty p : props) {
      propSet.add(p);
    }
    return getDefaultProjectSourceDirectory(extension, propSet);

  }

  /**
   * The project source directory for the language with the given extension and
   * properties.
   * <p>
   * See {@link SourceOptions#source} and {@link SourceOptions#testSource}
   * which override the default when specified.
   */
  public TypedFile getDefaultProjectSourceDirectory(
      FileExt extension, Iterable<SourceFileProperty> props) {

    ImmutableSet<SourceFileProperty> propSet =
        Sets.immutableEnumSet(props);

    if (FileExt.JAVA.equals(extension)) {
      if (!propSet.contains(SourceFileProperty.LOAD_AS_NEEDED)) {
        return new TypedFile(
            (propSet.contains(SourceFileProperty.TEST_ONLY)
             ? this.javaMainRoots
             : this.javaTestRoots)
            .get(0),
            propSet);
      }
    }

    File grandparent = propSet.contains(SourceFileProperty.LOAD_AS_NEEDED)
        ? this.depDir : this.srcDir;
    File parent = new File(
        grandparent,
        propSet.contains(SourceFileProperty.TEST_ONLY) ? "test" : "main");
    return new TypedFile(new File(parent, extension.extension), propSet);
  }
}
