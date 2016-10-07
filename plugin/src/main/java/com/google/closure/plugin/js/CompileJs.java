package com.google.closure.plugin.js;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.json.simple.JSONArray;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.plan.BundlingPlanGraphNode.OptionsAndBundles;
import com.google.closure.plugin.plan.CompilePlanGraphNode;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;
import com.google.closure.plugin.plan.Update;
import com.google.javascript.jscomp.CommandLineRunner;

final class CompileJs extends CompilePlanGraphNode<JsOptions, Modules> {

  public CompileJs(PlanContext context) {
    super(context);
  }

  @Override
  protected void process() throws IOException, MojoExecutionException {
    this.changedFiles.clear();
    this.processDefunctBundles(optionsAndBundles);
    Update<OptionsAndBundles<JsOptions, Modules>> u =
        this.optionsAndBundles.get();
    for (OptionsAndBundles<JsOptions, Modules> ob : u.changed) {
      processOne(ob.optionsAndInputs.options, ob.bundles.get(0));
    }
  }

  protected void processOne(JsOptions options, Modules modules)
  throws IOException, MojoExecutionException {
    final Log log = context.log;
    File jsOutputDir = new File(context.closureOutputDirectory, "js");
    java.nio.file.Files.createDirectories(jsOutputDir.toPath());

    if (modules.modules.isEmpty()) {
      log.info("Skipping JS compilation -- zero modules");
      return;
    }

    ImmutableList.Builder<String> argvBuilder = ImmutableList.builder();
    options.addArgv(log, argvBuilder);

    argvBuilder.add("--module_output_path_prefix")
        .add(jsOutputDir.getPath() + File.separator);

    argvBuilder.add("--create_renaming_reports");
    argvBuilder.add("--create_source_map").add("%outname%-source-map.json");

    ImmutableList.Builder<Source> jsSourcesBuilder = ImmutableList.builder();

    modules.addClosureCompilerFlags(argvBuilder, jsSourcesBuilder);
    // Tell the compiler that we're going to be passing inputs via the JSON
    // streaming API so that we can control the path associated with the
    // source file.
    // Closure Compiler compares the path to whitelists when doing conformance
    // checking, so we want to use paths relative to search roots.
    argvBuilder.add("--json_streams").add("BOTH");

    final ImmutableList<String> argv = argvBuilder.build();
    if (log.isDebugEnabled()) {
      log.debug("Executing JSCompiler: " + JSONArray.toJSONString(argv));
    }

    // Intercept stdout as a JSON stream of outputs and put the outputs in the
    // right place while building the bundle outputs list.
    JsonStreamOutputHandler stdoutReceiver = new JsonStreamOutputHandler(
        log);

    // Intercept stderr and map it to BuildContext messages.
    BuildContextMessageParser stderrReceiver = new BuildContextMessageParser(
        context.log, context.buildContext);

    List<Source> jsSources = jsSourcesBuilder.build();
    for (Source jsSource : jsSources) {
      context.buildContext.removeMessages(jsSource.canonicalPath);
    }

    try {
      ByteSource streamableJson = new StreamableJsonByteSource(log, jsSources);
      // TODO: See if Soy or Proto produce SourceMaps under some flag
      // configuration and forward them through.

      class RunCompiler extends Streamer {
        @Override
        void stream(InputStream stdin, PrintStream stdout, PrintStream stderr)
        throws MojoExecutionException {
          CommandLineRunner runner = new CommandLineRunner(
              argv.toArray(new String[0]),
              stdin, stdout, stderr) {
            // Subclass to get access to the constructor.
          };

          final long t0 = System.nanoTime();

          final class ExitCodeReceiver implements Function<Integer, Void> {
            Optional<Integer> exitCodeOpt = Optional.absent();

            @Override
            public Void apply(Integer exitCode) {
              long t1 = System.nanoTime();
              long dtMillis = (t1 - t0) / 1000000 /* ns / ms */;
              Preconditions.checkState(!exitCodeOpt.isPresent());
              exitCodeOpt = Optional.of(exitCode);
              String message = "jscomp exited with exit code " + exitCode
                  + " after " + dtMillis + " ms";
              if (exitCode != 0) {
                log.error(message);
              } else {
                log.info(message);
              }
              return null;
            }
          }
          final ExitCodeReceiver ecr = new ExitCodeReceiver();
          runner.setExitCodeReceiver(ecr);
          runner.run();
          if (runner.hasErrors()
              || !ecr.exitCodeOpt.isPresent()
              || ecr.exitCodeOpt.get() != 0) {
            throw new MojoExecutionException("JS compilation failed");
          }
        }
      }

      new RunCompiler().stream(
          streamableJson,
          stdoutReceiver,
          stderrReceiver);

    } catch (IOException ex) {
      throw new MojoExecutionException("JS compilation failed", ex);
    }

    try {
      stdoutReceiver.waitUntilAllClosed();
    } catch (InterruptedException ex) {
      throw new MojoExecutionException(
          "JS compilation interrupted waiting to receive streamed outputs", ex);
    }
    ImmutableList.Builder<File> outputFiles = ImmutableList.builder();
    for (JsonStreamOutputHandler.FileContents output
         : stdoutReceiver.getOutputs()) {
      File outputFile = new File(output.path);
      try {
        Files.createParentDirs(outputFile);
        Files.write(output.contents, outputFile, Charsets.UTF_8);
      } catch (IOException ex) {
        throw new MojoExecutionException(
            "Error writing Closure Compiler output " + output.path, ex);
      }
      outputFiles.add(outputFile);
    }

    this.bundleToOutputs.put(modules, outputFiles.build());
    List<MojoExecutionException> errors = stdoutReceiver.getFailures();
    if (!errors.isEmpty()) {
      int n = errors.size();
      MojoExecutionException error = errors.get(n - 1);
      for (int i = 0; i < n - 1; ++i) {
        log.error(errors.get(i));
      }
      throw error;
    }
  }

  @Override
  protected SV getStateVector() {
    return new SV(this);
  }


  static final class SV
  extends CompilePlanGraphNode.CompileStateVector<JsOptions, Modules> {

    private static final long serialVersionUID = 1L;

    protected SV(CompileJs node) {
      super(node);
    }

    @Override
    public PlanGraphNode<?> reconstitute(PlanContext c, JoinNodes jn) {
      return apply(new CompileJs(c));
    }
  }
}
