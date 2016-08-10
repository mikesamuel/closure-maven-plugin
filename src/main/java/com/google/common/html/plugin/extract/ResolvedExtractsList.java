package com.google.common.html.plugin.extract;

import java.io.File;
import java.io.Serializable;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.html.plugin.common.SourceFileProperty;

/**
 * Like an {@link ExtractsList} but after all the extracts have been resolved
 * against a list of actual dependencies.
 */
public final class ResolvedExtractsList implements Serializable {

  private static final long serialVersionUID = -584345869897976296L;

  /** Like {@link Extract} but with all the optional parts filled in. */
  public static final class ResolvedExtract implements Serializable {
    private static final long serialVersionUID = 3301199272591625007L;

    /** @see Extract#getGroupId() */
    public final String groupId;
    /** @see Extract#getArtifactId() */
    public final String artifactId;
    /** @see Extract#getVersion() */
    public final String version;
    /** @see Extract#getSuffixes() */
    public final ImmutableSet<String> suffixes;
    /**
     * True if the dependency is test-scope in which case files will be
     * extracted under {@code target/src/<filetype>/test} instead of
     * {@code target/src/<filetype>/main}.
     */
    public final ImmutableSet<SourceFileProperty> props;
    /** The location of the jar file. */
    public final File archive;

    /** */
    public ResolvedExtract(
        String groupId,
        String artifactId,
        String version,
        ImmutableSet<String> suffixes,
        ImmutableSet<SourceFileProperty> props,
        File archive) {
      this.groupId = groupId;
      this.artifactId = artifactId;
      this.version = version;
      this.suffixes = suffixes;
      this.props = props;
      this.archive = archive;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(
          groupId, artifactId, version, suffixes, props, archive);
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof ResolvedExtract)) {
        return false;
      }
      ResolvedExtract that = (ResolvedExtract) o;
      return this.groupId.equals(that.groupId)
          && this.artifactId.equals(that.artifactId)
          && this.version.equals(that.version)
          && this.suffixes.equals(that.suffixes)
          && this.props.equals(that.props)
          && this.archive.equals(that.archive);
    }

    @Override
    public String toString() {
      return "{" + groupId + ":" + artifactId + ":" + version
          + " => " + archive + "}";
    }
  }

  /** */
  public final ImmutableList<ResolvedExtract> extracts;

  /** */
  ResolvedExtractsList(Iterable<? extends ResolvedExtract> extracts) {
    this.extracts = ImmutableList.copyOf(extracts);
  }
}
