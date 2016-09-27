package com.google.closure.plugin.soy;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.closure.plugin.common.SourceFileProperty;
import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.StructurallyComparable;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.proto.SoyProtoTypeProvider;

/**
 * Builds a soy file set from options and context and makes sure that, for
 * a single run, the file set is shared between all compiles that need it
 * so that we don't generate duplicate error messages and warnings when we
 * apply different backends to the inputs.
 */
final class SoyFileSetSupplier implements Serializable, StructurallyComparable {
  private static final long serialVersionUID = -1120612669828338378L;

  private transient SoyFileSet sfs;
  private transient PlanContext context;
  private transient SoyOptions options;
  private transient ImmutableList<Source> sources;

  @SuppressWarnings("hiding")
  void init(
      PlanContext context, SoyOptions options, ImmutableList<Source> sources) {
    if (context != this.context
        || options != this.options
        || sources != this.sources) {
      this.sfs = null;
      this.context = context;
      this.options = options;
      this.sources = sources;
    }
  }

  synchronized SoyFileSet getSoyFileSet()
  throws MojoExecutionException{
    if (sfs != null) {
      return sfs;
    }

    SoyFileSet.Builder sfsBuilder = options.toSoyFileSetBuilder(context.log);

    for (Source source : sources) {
      String relPath = source.relativePath.getPath();
      SoyFileKind kind =
          source.root.ps.contains(SourceFileProperty.LOAD_AS_NEEDED)
          ? SoyFileKind.DEP
              : SoyFileKind.SRC;
      try {
        CharSequence content = Files.toString(
            source.canonicalPath, Charsets.UTF_8);
        sfsBuilder.addWithKind(content, kind, relPath);
      } catch (IOException ex) {
        throw new MojoExecutionException(
            "Failed to read soy source: " + relPath, ex);
      }
    }

    // Link the proto descriptors into the Soy type system so that Soy can
    // generate efficient JS code and so that Soy can avoid over-escaping of
    // safe-contract-type protos.
    Optional<File> mainDescriptorSetFileOpt =
        context.protoIO.getMainDescriptorSetFile();
    if (!mainDescriptorSetFileOpt.isPresent()) {
      throw new MojoExecutionException(
          "Cannot find where the proto planner put the descriptor files");
    }

    File mainDescriptorSetFile = mainDescriptorSetFileOpt.get();
    context.log.debug(
        "soy using proto descriptor file " + mainDescriptorSetFile);

    SoyProtoTypeProvider protoTypeProvider = null;
    try {
      if (mainDescriptorSetFile.exists()) {
        protoTypeProvider = new SoyProtoTypeProvider.Builder()
            // TODO: do we need to extract descriptor set files from
            // <extract>ed dependencies and include them here?
            .addFileDescriptorSetFromFile(mainDescriptorSetFile)
            .build();
      } else {
        context.log.info(
            "soy skipping missing descriptor file "
            + mainDescriptorSetFile);
      }
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Soy couldn't read proto descriptors from "
          + mainDescriptorSetFile,
          ex);
    } catch (DescriptorValidationException ex) {
      throw new MojoExecutionException(
          "Malformed proto descriptors in " + mainDescriptorSetFile,
          ex);
    }
    if (protoTypeProvider != null) {
      SoyTypeRegistry typeRegistry = new SoyTypeRegistry(
          ImmutableSet.<SoyTypeProvider>of(protoTypeProvider));
      sfsBuilder.setLocalTypeRegistry(typeRegistry);
    }

    this.sfs = sfsBuilder.build();
    return sfs;
  }

  @Override
  public int hashCode() {
    return options.hashCode() + 31 * sources.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || o.getClass() != getClass()) { return false; }
    SoyFileSetSupplier that = (SoyFileSetSupplier) o;
    return this.context == that.context
        // Ignore sfs which should be determinable from options & sources
        && this.options.equals(that.options)
        && this.sources.equals(that.options);
  }
}