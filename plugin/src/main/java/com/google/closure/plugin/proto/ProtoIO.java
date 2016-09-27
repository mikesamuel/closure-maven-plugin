package com.google.closure.plugin.proto;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.closure.plugin.common.ToolFinder;
import com.google.closure.plugin.plan.PlanContext;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * Protobuf compiler inputs and outputs derived from the proto options and
 * file-system.
 */
public final class ProtoIO {

  /** Descriptor set output file. */
  Optional<File> mainDescriptorSetFile = Optional.absent();
  /** Test-only descriptor set output file. */
  Optional<File> testDescriptorSetFile = Optional.absent();
  /** Locates protoc lazily. */
  Optional<ToolFinder<ProtoFinalOptions>> protocFinder = Optional.absent();

  final ToolFinder.Sink protoc = new ToolFinder.Sink();

  /** Production descriptor set output file. */
  public Optional<File> getMainDescriptorSetFile() {
    return mainDescriptorSetFile;
  }

  /** Test-only descriptor set output file. */
  public Optional<File> getTestDescriptorSetFile() {
    return testDescriptorSetFile;
  }

  ImmutableList<File> getProtoc(
      PlanContext context, ProtoFinalOptions options)
  throws MojoExecutionException {
    if (options.protocExec.isPresent()) {
      return ImmutableList.of(options.protocExec.get());
    }
    Optional<File> pc;
    synchronized (protoc) {
      pc = protoc.get();
      if (!pc.isPresent()) {
        if (protocFinder.isPresent()) {
          protocFinder.get().find(context.log, options, protoc);
        }
        pc = protoc.get();
      }
    }
    if (pc.isPresent()) {
      return ImmutableList.of(pc.get());
    }
    return ImmutableList.of();
  }
}
