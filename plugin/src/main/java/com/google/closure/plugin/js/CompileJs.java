package com.google.closure.plugin.js;

import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.json.simple.JSONArray;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
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
    this.outputFiles.clear();
    Update<OptionsAndBundles<JsOptions, Modules>> u =
        this.optionsAndBundles.get();
    for (@SuppressWarnings("unused")
         OptionsAndBundles<JsOptions, Modules> ob : u.defunct) {
      // TODO: figure out path name of output files and delete them.
    }
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

    ImmutableList.Builder<Source> jsSources = ImmutableList.builder();

    modules.addClosureCompilerFlags(argvBuilder, jsSources);
    // Tell the compiler that we're going to be passing inputs via the JSON
    // streaming API so that we can control the path associated with the
    // source file.
    // Closure Compiler compares the path to whitelists when doing conformance
    // checking, so we want to use paths relative to search roots.
    argvBuilder.add("--json_streams").add("IN");

    final ImmutableList<String> argv = argvBuilder.build();
    if (log.isDebugEnabled()) {
      log.debug("Executing JSCompiler: " + JSONArray.toJSONString(argv));
    }

    // TODO: Fix jscompiler so I can thread MavenLogJSErrorManager
    // through instead of mucking around with stdout, stderr
    // or intercept stdout and map it back to buildcontext.
    try {
      ByteSource streamableJson = new StreamableJsonByteSource(
          log, jsSources.build());

      logViaErrStreams(
          "jscomp: ",
          log,
          streamableJson,
          new WithStdOutAndStderrAndStdin() {
            @Override
            void run(InputStream stdin, PrintStream stdout, PrintStream stderr)
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
          });
    } catch (IOException ex) {
      throw new MojoExecutionException("JS compilation failed", ex);
    }

    // TODO: use json_streams both and get the list of output files.
    this.outputFiles.add(jsOutputDir);
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


  static abstract class PrefixingOutputStream extends FilterOutputStream {
    private final String prefix;

    @SuppressWarnings("resource")
    PrefixingOutputStream(String prefix) {
      super(new ByteArrayOutputStream());
      this.prefix = prefix;
    }


    protected abstract void onLine(CharSequence cs);

    @Override
    public void flush() throws IOException {
      doFlush(false);
    }

    @Override
    public void close() throws IOException {
      doFlush(true);

    }

    private void doFlush(boolean hard) throws IOException {
      ByteArrayOutputStream bytes = (ByteArrayOutputStream) this.out;

      byte[] byteArray = bytes.toByteArray();
      bytes.reset();

      int wrote = 0;
      int n = byteArray.length;
      for (int i = 0; i < n; ++i) {
        byte b = byteArray[i];
        if (b == '\n') {  // Assumes UTF-8 or ASCII
          onLine(
              new StringBuilder()
              .append(prefix)
              .append(new String(byteArray, wrote, i - wrote, "UTF-8")));
          wrote = i + 1;
        }
      }

      if (wrote < n) {
        if (hard) {
          // Assumes stream not closed in the middle of a UTF-8 sequence.
          onLine(
              new StringBuilder().append(prefix)
              .append(new String(byteArray, wrote, n - wrote, "UTF-8")));
        } else {
          // Save prefix of next line.
          bytes.write(byteArray, wrote, n - wrote);
        }
      }
    }
  }


  private static void logViaErrStreams(
      String prefix, final Log log, ByteSource input,
      WithStdOutAndStderrAndStdin withStdOutAndStderrAndStdin)
  throws IOException, MojoExecutionException {
    try (InputStream in = input.openBufferedStream()) {
      try (PrefixingOutputStream infoWriter =
          new PrefixingOutputStream(prefix) {
        @Override
        protected void onLine(CharSequence line) {
          log.info(line);
        }
      }) {
        try (PrintStream out = new PrintStream(infoWriter, true, "UTF-8")) {
          try (PrefixingOutputStream warnWriter =
              new PrefixingOutputStream(prefix) {
            @Override
            protected void onLine(CharSequence line) {
              log.warn(line);
            }
          }) {
            try (PrintStream err = new PrintStream(warnWriter, true, "UTF-8")) {
              withStdOutAndStderrAndStdin.run(in, out, err);
            }
          }
        }
      }
    }
  }

  abstract class WithStdOutAndStderrAndStdin {
    abstract void run(InputStream stdin, PrintStream stdout, PrintStream stderr)
    throws MojoExecutionException;
  }
}
