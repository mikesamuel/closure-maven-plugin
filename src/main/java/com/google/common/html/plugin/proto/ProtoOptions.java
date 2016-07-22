package com.google.common.html.plugin.proto;

import java.io.File;

import com.google.common.html.plugin.Options;

/**
 * Options for protoc.
 */
public final class ProtoOptions extends Options {
  private static final long serialVersionUID = -5667643473298285485L;

  /**
   * Source file roots.
   */
  public File[] source;

  /**
   * Test file roots.
   */
  public File[] testSource;

  /**
   * Protobuf version to compile schema files for.  If omitted,
   * version is inferred from the project's depended-on
   * com.google.com:protobuf-java artifact, if any.  (If both are
   * present, the version must match.)
   */
  public String protobufVersion;

  /**
   * Path to existing protoc to use.  Overrides auto-detection and
   * use of bundled protoc.
   */
  public File protocExec;

  /**
   * Path of output descriptor set file.
   * TODO: link
   */
  public File descriptorSetFile;

  /**
   * Path of output descriptor set file or test protos.
   * TODO: link
   */
  public File testDescriptorSetFile;

  @Override
  public ProtoOptions clone() throws CloneNotSupportedException {
    return (ProtoOptions) super.clone();
  }
}
