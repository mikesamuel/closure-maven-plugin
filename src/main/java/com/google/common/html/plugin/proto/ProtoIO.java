package com.google.common.html.plugin.proto;

import java.io.File;
import java.io.Serializable;

import com.google.common.collect.ImmutableList;

/**
 * Protobuf compiler inputs and outputs derived from the proto options and
 * file-system.
 */
public final class ProtoIO implements Serializable {
  private static final long serialVersionUID = 4437802371961966065L;

  /** Source roots for production .proto files. */
  public final ImmutableList<File> mainSourceRoots;
  /** Source roots for test-only .proto files. */
  public final ImmutableList<File> testSourceRoots;
  /** Descriptor set output file. */
  public final File mainDescriptorSetFile;
  /** Test-only descriptor set output file. */
  public final File testDescriptorSetFile;

  /** */
  public ProtoIO(
      Iterable<? extends File> mainSourceRoots,
      Iterable<? extends File> testSourceRoots,
      File mainDescriptorSetFile,
      File testDescriptorSetFile) {
    this.mainSourceRoots = ImmutableList.copyOf(mainSourceRoots);
    this.testSourceRoots = ImmutableList.copyOf(testSourceRoots);
    this.mainDescriptorSetFile = mainDescriptorSetFile;
    this.testDescriptorSetFile = testDescriptorSetFile;
  }
}
