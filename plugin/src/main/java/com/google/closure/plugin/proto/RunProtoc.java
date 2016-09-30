package com.google.closure.plugin.proto;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.common.SourceFileProperty;
import com.google.closure.plugin.common.TypedFile;
import com.google.closure.plugin.plan.BundlingPlanGraphNode.OptionsAndBundles;
import com.google.closure.plugin.plan.CompilePlanGraphNode;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.Update;

final class RunProtoc
extends CompilePlanGraphNode<ProtoFinalOptions, ProtoBundle> {

  RunProtoc(PlanContext context) {
    super(context);
  }

  @Override
  protected void process() throws IOException, MojoExecutionException {
    outputFiles.clear();

    Update<OptionsAndBundles<ProtoFinalOptions, ProtoBundle>> u =
        this.optionsAndBundles.get();

    for (@SuppressWarnings("unused")
         OptionsAndBundles<ProtoFinalOptions, ProtoBundle> d : u.defunct) {
      // TODO: delete defunct outputs
    }

    for (OptionsAndBundles<ProtoFinalOptions, ProtoBundle> c : u.changed) {
      for (ProtoBundle b : c.bundles) {
        processOne(c.optionsAndInputs.options, b);
      }
    }
  }

  protected void processOne(ProtoFinalOptions options, ProtoBundle bundle)
  throws IOException, MojoExecutionException {
    ImmutableList<Source> sources = bundle.inputs;

    if (Iterables.isEmpty(sources)) {
      // We're done.
      // TODO: Is it a problem that we will not generate
      // an empty descriptor set file?
      return;
    }

    ImmutableList<File> protocs = context.protoIO.getProtoc(context, options);
    if (protocs.isEmpty()) {
      throw new MojoExecutionException(
          "No protoc executable found."
          + "  Maybe specify <configuration><proto><protocExecutable>..."
          + " or make sure you have a dependency on protobuf-java");
    }
    File protoc = protocs.get(0);

    ImmutableList.Builder<String> argv = ImmutableList.builder();
    ProtoPathBuilder protoPathBuilder = new ProtoPathBuilder(argv);
    argv.add(protoc.getPath());

    if (bundle.langSet == LangSet.ALL && bundle.descriptorSetFile.isPresent()) {
      File descriptorSetFile = bundle.descriptorSetFile.get();
      argv.add("--include_imports");
      argv.add("--descriptor_set_out")
          .add(descriptorSetFile.getPath());
      Files.createParentDirs(descriptorSetFile);
      outputFiles.add(descriptorSetFile);
    }

    File javaGenfilesPath = bundle.rootSet == RootSet.TEST
        ? context.genfilesDirs.javaTestGenfiles
        : context.genfilesDirs.javaGenfiles;
    File jsGenfilesPath = bundle.rootSet == RootSet.TEST
        ? context.genfilesDirs.jsTestGenfiles
        : context.genfilesDirs.jsGenfiles;

    // Protoc is a little finicky about requiring that output directories
    // exist, though it will happily create directories for the packages.
    if (bundle.langSet.emitJava) {
      argv.add("--java_out").add(ensureDirExists(javaGenfilesPath));
      outputFiles.add(javaGenfilesPath);  // TODO: narrow
    }
    if (bundle.langSet.emitJs) {
      String jsOutFlagPrefix = "";
      switch (bundle.rootSet) {
        case MAIN:
          jsOutFlagPrefix = "--js_out=";
          break;
        case TEST:
          // github.com/google/protobuf/blob/master/js/README.md#the---js_out-flag
          jsOutFlagPrefix += "--js_out=testonly:";
          break;
      }
      argv.add(jsOutFlagPrefix + ensureDirExists(jsGenfilesPath));
      outputFiles.add(jsGenfilesPath);  // TODO: narrow
    }

    // Build a proto search path.
    for (TypedFile root : options.sources.roots) {
      if (bundle.rootSet == RootSet.TEST
           || !root.ps.contains(SourceFileProperty.TEST_ONLY)) {
        protoPathBuilder.withRoot(root.f);
      }
    }

    for (Source input : bundle.inputs) {
      TypedFile root = input.root;
      if (root.f.exists()) {
        protoPathBuilder.withRoot(root.f);
        // We're not guarding against ambiguity here.
        // We warn on it below.
      }
    }

    // Inputs shouldn't start with "-", but just in case.
    //argv.add("--");  // protoc does not recognize "--".

    // Check for obvious sources of ambiguity due to two inputs with the
    // same relative path.  We pass absolute paths to protoc, but the
    // paths resolved by `import "<relative-path>";` directives are still
    // a potential source of ambiguity.
    Map<File, Source> relPathToSource = Maps.newHashMap();
    for (Source input : bundle.inputs) {
      Source ambig = relPathToSource.put(input.relativePath, input);
      if (ambig == null) {
        argv.add(
            // Instead of using canonicalPath, we concat these two paths
            // because protoc insists that each input appear under a
            // search path element as determined by string comparison.
            FilenameUtils.concat(
                input.root.f.getPath(),
                input.relativePath.getPath()));
      } else {
        context.log.warn(
            "Ambiguous proto input " + input.relativePath
            + " appears on search path twice: "
            + ambig.root + " and " + input.root);
      }
    }

    // TODO: Feed errors and warnings back to the buildContext
    Future<Integer> exitCodeFuture = context.processRunner.run(
        context.log, "protoc", argv.build());
    try {
      Integer exitCode = exitCodeFuture.get(30, TimeUnit.SECONDS);

      if (exitCode.intValue() != 0) {
        throw new MojoExecutionException(
            "protoc execution failed with exit code " + exitCode);
      }
    } catch (TimeoutException ex) {
      throw new MojoExecutionException("protoc execution timed out", ex);
    } catch (InterruptedException ex) {
      throw new MojoExecutionException("protoc execution was interrupted", ex);
    } catch (ExecutionException ex) {
      throw new MojoExecutionException("protoc execution failed", ex);
    } catch (CancellationException ex) {
      throw new MojoExecutionException("protoc execution was cancelled", ex);
    }
  }

  private static String ensureDirExists(File dirPath) throws IOException {
    java.nio.file.Files.createDirectories(dirPath.toPath());
    return dirPath.getPath();
  }

  enum RootSet {
    MAIN,
    TEST,
    ;
  }

  enum LangSet {
    ALL(true, true),
    JAVA_ONLY(true, false),
    JS_ONLY(false, true),
    ;

    final boolean emitJava;
    final boolean emitJs;

    LangSet(boolean emitJava, boolean emitJs) {
      this.emitJava = emitJava;
      this.emitJs = emitJs;
    }
  }


  final class ProtoPathBuilder {
    private final Set<String> seen = Sets.newHashSet();
    private final ImmutableList.Builder<? super String> argv;

    ProtoPathBuilder(ImmutableList.Builder<? super String> argv) {
      this.argv = argv;
    }

    void withRoot(File f) throws MojoExecutionException {
      // protoc complains about non-extant search path elements.
      if (f.exists()) {
        // If a path element is specified multiple times, protoc complains about
        // declaration masking.
        String canonPath;
        try {
          canonPath = f.getCanonicalPath();
          // TODO: Should all roots be canonicalized at Source's constructor?
          // Will that suffice if those roots don't yet exist but are later
          // created by File.mkdirs as is done multiple places during the course
          // of plugin execution?
        } catch (IOException ex) {
          throw new MojoExecutionException(
              "Failed to canonicalize proto search path element: " + f, ex);
        }
        if (seen.add(canonPath)) {
          argv.add("--proto_path");
          argv.add(canonPath);
        }
      }
    }
  }

  @Override
  protected SV getStateVector() {
    return new SV(this);
  }

  static final class SV
  extends CompileStateVector<ProtoFinalOptions, ProtoBundle> {

    private static final long serialVersionUID = 6399733844048652746L;

    protected SV(RunProtoc node) {
      super(node);
    }

    @Override
    public RunProtoc reconstitute(PlanContext c, JoinNodes jn) {
      return apply(new RunProtoc(c));
    }
  }
}
