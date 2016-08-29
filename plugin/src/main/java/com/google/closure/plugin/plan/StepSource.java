package com.google.closure.plugin.plan;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * An abstract representation of a group of hashable items used to order
 * {@link Step} execution.
 */
public enum StepSource {
  /**
   * Intermediate file containing a list of extracts with full
   * artifact specifiers.
   */
  RESOLVED_EXTRACTS("target/.closure-cache/resolved-extracts.ser"),

  /** CSS source files for the current project. */
  CSS_SRC("src/main/css/**/*.css"),
  /**
   * Generated CSS source files for the current project
   * including those extracted from dependencies.
   */
  CSS_GENERATED("target/src/main/css/**/*.css"),
  /**
   * Compiled (minified) CSS files that are suitable for a browser but which may
   * lack the annotations needed by the toolchain to use as inputs.
   */
  CSS_COMPILED("target/classes/closure/src/main/css/**/*.css"),
  /** Maps lines in compiled CSS outputs to lines in CSS source files. */
  CSS_SOURCE_MAP("target/css/**/*-source-map.json"),
  /**
   * Maps identifiers in CSS sources to minified identifiers in the compiled
   * output.
   */
  CSS_RENAME_MAP("target/css/rename-map.json"),

  /** Generated Java source files. */
  JAVA_GENERATED("src/main/java/**/*.java"),

  /** JavaScript source files for the current project. */
  JS_SRC("src/main/js/**/*.js"),
  /**
   * Generated JavaScript source files including those extracted from
   * dependencies.
   */
  JS_GENERATED("target/src/main/js/**/*.js"),
  /**
   * Compiled (minified) JavaScript files that are suitable for a browser but
   * which may lack types and other annotations needed by the toolchain to
   * use as inputs.
   */
  JS_COMPILED("target/classes/closure/src/main/js/**/*.js"),
  /**
   * Maps lines in compiled JavaScript outputs to lines in JavaScript source
   * files.
   */
  JS_SOURCE_MAP("target/js/*-source-map.json"),
  /**
   * An intermediate file that records a mapping from source files to
   * lists of provides/requires.
   */
  JS_DEP_INFO("target/.closure-cache/dep-info.ser"),
  /**
   * An intermediate file that records a relationships between modules and
   * which sources within those modules are needed.
   */
  JS_MODULES("target/.closure-cache/modules.ser"),

  /** Protobuf source files for the current project. */
  PROTO_SRC("src/main/js/**/*.proto"),
  /**
   * Generated Protobuf source files including those extracted from
   * dependencies.
   */
  PROTO_GENERATED("target/genfiles/src/main/css/**/*.proto"),
  /**
   * Protobuf descriptor set files which describe the structure of protocol
   * buffers to enable reflection over messages.
   */
  PROTO_DESCRIPTOR_SET("target/src/main/proto/descriptor_set.fd"),
  /**
   * The protocol buffer compiler executable.
   */
  PROTOC("/usr/bin/protoc"),
  /**
   * An intermediate input that maps .proto files including dependencies to
   * their package declarations.
   */
  PROTO_PACKAGE_MAP("target/.closure-cache/proto-package-map.ser"),

  /** Soy source files for the current project. */
  SOY_SRC("src/main/soy/**/*.soy"),
  /**
   * Generated Soy source files for the current project
   * including those extracted from dependencies.
   */
  SOY_GENERATED("target/genfiles/src/main/soy/**/*.soy"),
  ;

  /** Human readable string that resembles the default layout. */
  public final String displayString;

  /** All the {@code *_GENERATED} step sources. */
  public static final ImmutableSet<StepSource> ALL_GENERATED =
      Sets.immutableEnumSet(
          CSS_GENERATED, JAVA_GENERATED, JS_GENERATED, PROTO_GENERATED,
          SOY_GENERATED);

  /** All the {@code *_COMPILED} step sources. */
  public static final ImmutableSet<StepSource> ALL_COMPILED =
      Sets.immutableEnumSet(
      CSS_COMPILED, JS_COMPILED);

  StepSource(String displayString) {
    this.displayString = displayString;
  }

  @Override
  public String toString() {
    return this.displayString;
  }
}
