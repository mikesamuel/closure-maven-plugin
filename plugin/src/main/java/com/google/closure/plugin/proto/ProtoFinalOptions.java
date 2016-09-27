package com.google.closure.plugin.proto;

import java.io.File;
import java.io.Serializable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.closure.plugin.common.DirectoryScannerSpec;
import com.google.closure.plugin.common.Options;
import com.google.closure.plugin.plan.StructurallyComparable;

/**
 * An immutable representation of the same data as {@link ProtoOptions} that
 * includes default path information derived from the project configuration.
 */
public final class ProtoFinalOptions
implements Serializable, StructurallyComparable {

  private static final long serialVersionUID = -8936056156839617489L;

  /** {@link Options#getId()}. */
  final String id;

  /** Source file specification. */
  final DirectoryScannerSpec sources;

  /**
   * Protobuf version to compile schema files for.  If omitted,
   * version is inferred from the project's depended-on
   * com.google.com:protobuf-java artifact, if any.  (If both are
   * present, the version must match.)
   */
  public final Optional<String> protobufVersion;

  /**
   * Path to existing protoc to use.  Overrides auto-detection and
   * use of bundled protoc.
   */
  public final Optional<File> protocExec;

  /**
   * Path of output descriptor set file.
   * @see <a href="https://developers.google.com/protocol-buffers/docs/reference/java/com/google/protobuf/Descriptors">Descriptors</a>
   */
  final File descriptorSetFile;

  /**
   * Path of output descriptor set file or test protos.
   * @see <a href="https://developers.google.com/protocol-buffers/docs/reference/java/com/google/protobuf/Descriptors">Descriptors</a>
   */
  final File testDescriptorSetFile;

  /**
   * Protobuf packages to only compile only to JS.
   * If a package appears on the jsOnly and {@link #javaOnly} lists
   * then it will be compiled to both.
   */
  final ImmutableSet<String> jsOnly;

  /**
   * Protobuf packages to only compile only to Java.
   * If a package appears on the {@link #jsOnly} and javaOnly lists
   * then it will be compiled to both.
   */
  final ImmutableSet<String> javaOnly;

  ProtoFinalOptions(
      String id,
      DirectoryScannerSpec sources,
      Optional<String> protobufVersion,
      Optional<File> protocExec,
      File descriptorSetFile,
      File testDescriptorSetFile,
      ImmutableSet<String> jsOnly,
      ImmutableSet<String> javaOnly) {
    this.id = Preconditions.checkNotNull(id);
    this.sources = Preconditions.checkNotNull(sources);
    this.protobufVersion = Preconditions.checkNotNull(protobufVersion);
    this.protocExec = Preconditions.checkNotNull(protocExec);
    this.descriptorSetFile = Preconditions.checkNotNull(descriptorSetFile);
    this.testDescriptorSetFile =
        Preconditions.checkNotNull(testDescriptorSetFile);
    this.jsOnly = Preconditions.checkNotNull(jsOnly);
    this.javaOnly = Preconditions.checkNotNull(javaOnly);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((descriptorSetFile == null) ? 0 : descriptorSetFile.hashCode());
    result = prime * result + ((id == null) ? 0 : id.hashCode());
    result = prime * result + ((javaOnly == null) ? 0 : javaOnly.hashCode());
    result = prime * result + ((jsOnly == null) ? 0 : jsOnly.hashCode());
    result = prime * result + ((protobufVersion == null) ? 0 : protobufVersion.hashCode());
    result = prime * result + ((protocExec == null) ? 0 : protocExec.hashCode());
    result = prime * result + ((sources == null) ? 0 : sources.hashCode());
    result = prime * result + ((testDescriptorSetFile == null) ? 0 : testDescriptorSetFile.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    ProtoFinalOptions other = (ProtoFinalOptions) obj;
    if (descriptorSetFile == null) {
      if (other.descriptorSetFile != null) {
        return false;
      }
    } else if (!descriptorSetFile.equals(other.descriptorSetFile)) {
      return false;
    }
    if (id == null) {
      if (other.id != null) {
        return false;
      }
    } else if (!id.equals(other.id)) {
      return false;
    }
    if (javaOnly == null) {
      if (other.javaOnly != null) {
        return false;
      }
    } else if (!javaOnly.equals(other.javaOnly)) {
      return false;
    }
    if (jsOnly == null) {
      if (other.jsOnly != null) {
        return false;
      }
    } else if (!jsOnly.equals(other.jsOnly)) {
      return false;
    }
    if (protobufVersion == null) {
      if (other.protobufVersion != null) {
        return false;
      }
    } else if (!protobufVersion.equals(other.protobufVersion)) {
      return false;
    }
    if (protocExec == null) {
      if (other.protocExec != null) {
        return false;
      }
    } else if (!protocExec.equals(other.protocExec)) {
      return false;
    }
    if (sources == null) {
      if (other.sources != null) {
        return false;
      }
    } else if (!sources.equals(other.sources)) {
      return false;
    }
    if (testDescriptorSetFile == null) {
      if (other.testDescriptorSetFile != null) {
        return false;
      }
    } else if (!testDescriptorSetFile.equals(other.testDescriptorSetFile)) {
      return false;
    }
    return true;
  }
}
