package com.google.closure.plugin.extract;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.Set;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.closure.plugin.common.SourceFileProperty;

/**
 * Identifies an artifact to extract source files from.
 * <p>
 * Supporting {@code .css}, {@code .js}, {@code .proto}, and {@code .soy} files
 * can be packaged in a JAR file.
 * </p>
 */
public final class Extract implements Serializable {

  private static final long serialVersionUID = -220704355463069260L;

  /** The default list of file suffixes to extract from the artifact. */
  public static final ImmutableSet<String> DEFAULT_EXTRACT_SUFFIX_SET =
      ImmutableSet.of("css", "js", "proto", "soy");

  private Optional<String> groupId = Optional.absent();
  private Optional<String> artifactId = Optional.absent();
  private Optional<String> version = Optional.absent();
  private final Set<String> suffixes = Sets.newLinkedHashSet();
  private final EnumSet<SourceFileProperty> props = EnumSet.noneOf(
      SourceFileProperty.class);

  /** Zero-argument constructor for plexus configurator */
  public Extract() {
    // all absent
  }

  /** */
  public Extract(String artifactId) {
    setArtifactId(artifactId);
  }

  /** */
  public Extract(String groupId, String artifactId) {
    setGroupId(groupId);
    setArtifactId(artifactId);
  }

  /** */
  public Extract(String groupId, String artifactId, String version) {
    this(groupId, artifactId);
    setVersion(version);
  }

  /**
   * A plexus configurator "default setter" called when the configuration
   * element is abbreviated to {@code <extract>artifactId</extract>}.
   * <p>
   * This implementation takes either a {@code ':'} separated artifact
   * specifier.  If there are no colons it delegates to {@link #setArtifactId}.
   * If only an artifact is present, the extractor will try to disambiguate
   * by looking for a dependency with this artifact id.
   */
  public void set(String artifactSpecifier) {
    String[] parts = artifactSpecifier.split(":");
    switch (parts.length) {
      case 1:
        setArtifactId(parts[0]);
        break;
      case 2:
        setGroupId(parts[0]);
        setArtifactId(parts[1]);
        break;
      case 3:
        setGroupId(parts[0]);
        setArtifactId(parts[1]);
        setVersion(parts[2]);
        break;
      case 4:
        if (!"jar".equals(parts[2])) {
          throw new IllegalArgumentException(
              "Expected jar, not " + parts[2] + " in " + artifactSpecifier);
        }
        setGroupId(parts[0]);
        setArtifactId(parts[1]);
        setVersion(parts[3]);
        break;
      default:
        throw new IllegalArgumentException(
            "Expected artifact specifier, not " + artifactSpecifier);
    }
  }

  /** Setter that allows configuration by a plexus configurator. */
  public void setGroupId(String newGroupId) {
    this.groupId = Optional.of(newGroupId);
  }

  /** The group id of the artifact to extract from if present. */
  public Optional<String> getGroupId() {
    return groupId;
  }

  /** Setter that allows configuration by a plexus configurator. */
  public void setArtifactId(String newArtifactId) {
    this.artifactId = Optional.of(newArtifactId);
  }

  /** The artifact id of the artifact to extract from if present. */
  public Optional<String> getArtifactId() {
    return artifactId;
  }

  /** Setter that allows configuration by a plexus configurator. */
  public void setVersion(String newVersion) {
    this.version = Optional.of(newVersion);
  }

  /** Setter that allows configuration by a plexus configurator. */
  public void setProperty(SourceFileProperty p) {
    this.props.add(p);
  }

  /** Setter that allows configuration by a plexus configurator. */
  public void setTestOnly(boolean b) {
    setProperty(SourceFileProperty.TEST_ONLY, b);
  }

  /** Setter that allows configuration by a plexus configurator. */
  public void setLoadAsNeeded(boolean b) {
    setProperty(SourceFileProperty.LOAD_AS_NEEDED, b);
  }

  private void setProperty(SourceFileProperty p, boolean b) {
    if (b) {
      props.add(p);
    } else {
      props.remove(p);
    }
  }

  /** The version of the artifact to extract from if present. */
  public Optional<String> getVersion() {
    return version;
  }

  /**
   * Adds a suffix to the extract list.
   * By default, {@link #DEFAULT_EXTRACT_SUFFIX_SET} are extracted.
   * <p>
   * This does not clobber previously set values, so functions like
   * an adder.
   */
  public void setSuffix(String fileSuffix) {
    String suffix = fileSuffix;
    if (suffix.startsWith(".")) { suffix = suffix.substring(1); }
    this.suffixes.add(suffix);
  }

  /**
   * A set of suffixes of files to extract from the specified artifact's jar.
   */
  public ImmutableSet<String> getSuffixes() {
    ImmutableSet<String> fromBuilder = ImmutableSet.copyOf(this.suffixes);
    if (fromBuilder.isEmpty()) {
      return DEFAULT_EXTRACT_SUFFIX_SET;
    }
    return fromBuilder;
  }

  /**
   * The file properties for the extracted files.
   */
  public ImmutableSet<SourceFileProperty> getFileProperties() {
    return Sets.immutableEnumSet(this.props);
  }

  @Override
  public String toString() {
    return "{extract "
        + (groupId.isPresent() ? groupId.get() : "")
        + ":"
        + (artifactId.isPresent() ? artifactId.get() : "")
        + (version.isPresent() ? ":" + version.get() : "")
        + "}";
  }
}
