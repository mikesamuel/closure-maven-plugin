package com.google.common.html.plugin.proto;

import java.io.File;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.html.plugin.common.DirectoryScannerSpec;
import com.google.common.html.plugin.plan.KeyedSerializable;
import com.google.common.html.plugin.plan.PlanKey;

/**
 * An immutable representation of the same data as {@link ProtoOptions} that
 * includes default path information derived from the project configuration.
 */
public final class ProtoFinalOptions implements KeyedSerializable {

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
   * If a package appears on the {@link #jsOnly} and {@link #javaOnly} lists
   * then it will be compiled to both.
   */
  final ImmutableSet<String> jsOnly;

  /**
   * Protobuf packages to only compile only to Java.
   * If a package appears on the {@link #jsOnly} and {@link #jsOnly} lists
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
  public PlanKey getKey() {
    return PlanKey.builder("fopt")
        .addString(getClass().getName())
        .addString(id)
        .build();
  }
}
