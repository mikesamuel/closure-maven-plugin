package com.google.closure.plugin.proto;

import java.io.File;
import java.io.Serializable;

import com.google.closure.plugin.common.DirectoryScannerSpec;

/**
 * Protobuf compiler inputs and outputs derived from the proto options and
 * file-system.
 */
public final class ProtoIO implements Serializable {
  private static final long serialVersionUID = 4437802371961966065L;

  /** Specifies how to find .proto files. */
  public final DirectoryScannerSpec protoSources;
  /** Descriptor set output file. */
  public final File mainDescriptorSetFile;
  /** Test-only descriptor set output file. */
  public final File testDescriptorSetFile;

  /** */
  public ProtoIO(
      DirectoryScannerSpec protoSources,
      File mainDescriptorSetFile,
      File testDescriptorSetFile) {
    this.protoSources = protoSources;
    this.mainDescriptorSetFile = mainDescriptorSetFile;
    this.testDescriptorSetFile = testDescriptorSetFile;
  }
}
