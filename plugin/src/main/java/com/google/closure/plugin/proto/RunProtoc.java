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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.sonatype.plexus.build.incremental.BuildContext;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.common.ProcessRunner;
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
    this.changedFiles.clear();
    this.processDefunctBundles(this.optionsAndBundles);

    Update<OptionsAndBundles<ProtoFinalOptions, ProtoBundle>> u =
        this.optionsAndBundles.get();

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
      changedFiles.add(descriptorSetFile);
    }

    File javaDestDir = bundle.rootSet == RootSet.TEST
        ? context.genfilesDirs.javaTestGenfiles
        : context.genfilesDirs.javaGenfiles;
    File jsDestDir = bundle.rootSet == RootSet.TEST
        ? context.genfilesDirs.jsTestGenfiles
        : context.genfilesDirs.jsGenfiles;

    File javaTempDir = null;
    // Protoc is a little finicky about requiring that output directories
    // exist, though it will happily create directories for the packages.
    if (bundle.langSet.emitJava) {
      javaTempDir = Files.createTempDir();
      argv.add("--java_out").add(javaTempDir.getPath());
    }
    File jsTempDir = null;
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
      jsTempDir = Files.createTempDir();
      argv.add(jsOutFlagPrefix + ensureDirExists(jsTempDir));
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
        // Instead of using canonicalPath, we concat these two paths
        // because protoc insists that each input appear under a
        // search path element as determined by string comparison.
        File inputFile = new File(FilenameUtils.concat(
            input.root.f.getPath(),
            input.relativePath.getPath()));
        argv.add(inputFile.getPath());
        context.buildContext.removeMessages(inputFile);
      } else {
        context.log.warn(
            "Ambiguous proto input " + input.relativePath
            + " appears on search path twice: "
            + ambig.root + " and " + input.root);
      }
    }

    // Feed errors and warnings back to the buildContext
    Future<Integer> exitCodeFuture = context.processRunner.run(
        context.log, "protoc", argv.build(),
        new ProtocOutputReader(context.log, context.buildContext));
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

    ImmutableSet.Builder<File> filesForBundleBuilder = ImmutableSet.builder();
    if (javaTempDir != null) {
      copyFilesOver(javaTempDir, javaDestDir, filesForBundleBuilder);
    }
    if (jsTempDir != null) {
      copyFilesOver(jsTempDir, jsDestDir, filesForBundleBuilder);
    }

    ImmutableSet<File> filesForBundle = filesForBundleBuilder.build();
    ImmutableList<File> oldFiles = this.bundleToOutputs.put(
        bundle, ImmutableList.copyOf(filesForBundle));
    if (oldFiles != null) {
      for (File f : oldFiles) {
        if (!filesForBundle.contains(f)) {
          this.deleteIfExists(f);
        }
      }
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

  static final class ProtocOutputReader
  implements ProcessRunner.OutputReceiver {

    private static final Pattern MESSAGE = Pattern.compile(
        "^([^:]+):(\\d+):(\\d+): "
        // Message from libprotobuf
        + "|No syntax specified for file: (.*?)[.] Please"
        );

    private final Log log;
    private final BuildContext buildContext;

    ProtocOutputReader(Log log, BuildContext buildContext) {
      this.log = log;
      this.buildContext = buildContext;
    }

    @Override
    public void processLine(String line) {
      Matcher m = MESSAGE.matcher(line);
      if (m.find()) {
        String file = m.group(1);
        int lineno, column;
        String message;
        if (file != null) {
          lineno = Integer.parseInt(m.group(2));
          column = Integer.parseInt(m.group(3));
          message = line.substring(m.end());
        } else {
          file = m.group(4);
          lineno = column = 1;
          message = line;
        }
        // Assumes buildContext is internally synchronized.
        buildContext.addMessage(
            new File(file), lineno, column, message,
            BuildContext.SEVERITY_ERROR,
            null);
      } else {
        log.info("protoc: " + line);
      }
    }

    @Override
    public void allProcessed() {
      // No need to wait for output processing.
      // TODO: maybe detect if we're running on the command line and gate on
      // this so we get better log-locality.
    }
  }
}
