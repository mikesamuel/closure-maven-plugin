package com.google.common.html.plugin.proto;

import java.io.File;

import com.comoyo.maven.plugins.protoc.ProtocBundledMojo;
import com.google.common.base.Optional;
import com.google.common.html.plugin.Sources;

final class ProtoCompilerWrapper {
  private final ProtoOptions options;
  private Sources mainRoots = Sources.EMPTY;
  private Optional<File> destination = Optional.absent();
  private Optional<File> testDestination = Optional.absent();

  ProtoCompilerWrapper(ProtoOptions options) {
    this.options = options;
  }

  ProtoCompilerWrapper mainRoots(Sources newMainRoots) {
    this.mainRoots = newMainRoots;
    return this;
  }

  ProtoCompilerWrapper destination(File destDir) {
    this.destination = Optional.of(destDir);
    return this;
  }

  ProtoCompilerWrapper testDestination(File testDestDir) {
    this.testDestination = Optional.of(testDestDir);
    return this;
  }

  void compileToJs() {
    // TODO
  }

  void compileToJava() {
    ProtocBundledMojo protoc = new ProtocBundledMojo();
    // TODO set options
    // TODO protoc.execute();
  }

}
