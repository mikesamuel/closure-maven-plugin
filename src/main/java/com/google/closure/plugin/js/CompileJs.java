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
import com.google.common.collect.Sets;
import com.google.closure.plugin.common.Ingredients.HashedInMemory;
import com.google.closure.plugin.common.Ingredients.PathValue;
import com.google.closure.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.closure.plugin.plan.Ingredient;
import com.google.closure.plugin.plan.PlanKey;
import com.google.closure.plugin.plan.Step;
import com.google.closure.plugin.plan.StepSource;
import com.google.javascript.jscomp.CommandLineRunner;

final class CompileJs extends Step {

  public CompileJs(
      HashedInMemory<JsOptions> optionsIng,
      SerializedObjectIngredient<Modules> modulesIng,
      PathValue jsOutputDir) {
    super(
        PlanKey.builder("compile-js")
            .addInp(optionsIng, modulesIng, jsOutputDir)
            .build(),
        ImmutableList.<Ingredient>of(optionsIng, modulesIng, jsOutputDir),
        Sets.immutableEnumSet(StepSource.JS_SRC, StepSource.JS_GENERATED),
        Sets.immutableEnumSet(StepSource.JS_COMPILED, StepSource.JS_SOURCE_MAP)
        );
  }

  @Override
  public void execute(final Log log) throws MojoExecutionException {
    HashedInMemory<JsOptions> optionsIng =
        ((HashedInMemory<?>) inputs.get(0)).asSuperType(JsOptions.class);
    SerializedObjectIngredient<Modules> modulesIng =
        ((SerializedObjectIngredient<?>) inputs.get(1))
        .asSuperType(Modules.class);
    PathValue jsOutputDir = (PathValue) inputs.get(2);

    Modules modules = modulesIng.getStoredObject().get();
    if (modules.modules.isEmpty()) {
      log.info("Skipping JS compilation -- zero modules");
      return;
    }

    ImmutableList.Builder<String> argvBuilder = ImmutableList.builder();
    optionsIng.getValue().addArgv(log, argvBuilder);

    argvBuilder.add("--module_output_path_prefix")
        .add(jsOutputDir.value.getPath() + File.separator);
    jsOutputDir.value.mkdirs();

    argvBuilder.add("--create_renaming_reports");
    argvBuilder.add("--create_source_map").add("%outname%-source-map.json");

    modules.addClosureCompilerFlags(argvBuilder);

    final ImmutableList<String> argv = argvBuilder.build();
    if (log.isDebugEnabled()) {
      log.debug("Executing JSCompiler: " + JSONArray.toJSONString(argv));
    }

    // TODO: Fix jscompiler so I can thread MavenLogJSErrorManager
    // through instead of mucking around with stdout, stderr.
    try {
      logViaErrStreams(
          "jscomp: ",
          log,
          new WithStdOutAndStderrAndWithoutStdin() {
            @Override
            void run(InputStream stdin, PrintStream stdout, PrintStream stderr)
            throws IOException, MojoExecutionException {
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
  }

  @Override
  public void skip(Log log) throws MojoExecutionException {
    // Done.
  }

  @Override
  public ImmutableList<Step> extraSteps(Log log) throws MojoExecutionException {
    return ImmutableList.of();
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
              .append(new String(byteArray, wrote, i + 1, "UTF-8")));
          wrote = i + 1;
        }
      }

      if (wrote < n) {
        if (hard) {
          // Assumes stream not closed in the middle of a UTF-8 sequence.
          onLine(
              new StringBuilder().append(prefix)
              .append(new String(byteArray, wrote, n, "UTF-8")));
        } else {
          // Save prefix of next line.
          bytes.write(byteArray, wrote, n);
        }
      }
    }
  }


  static final class DevNullInputStream extends InputStream {
    @Override
    public int read() throws IOException {
      return -1;
    }
  }


  private static void logViaErrStreams(
      String prefix, final Log log,
      WithStdOutAndStderrAndWithoutStdin withStdOutAndStderrAndWithoutStdin)
  throws IOException, MojoExecutionException {
    try (InputStream in = new DevNullInputStream()) {
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
              withStdOutAndStderrAndWithoutStdin.run(in, out, err);
            }
          }
        }
      }
    }
  }

  abstract class WithStdOutAndStderrAndWithoutStdin {
    abstract void run(InputStream stdin, PrintStream stdout, PrintStream stderr)
    throws IOException, MojoExecutionException;
  }
}
