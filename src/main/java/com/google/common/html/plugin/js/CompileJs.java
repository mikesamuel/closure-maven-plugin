package com.google.common.html.plugin.js;

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
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients.PathValue;
import com.google.common.html.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.PlanKey;
import com.google.common.html.plugin.plan.Step;
import com.google.common.html.plugin.plan.StepSource;
import com.google.javascript.jscomp.CommandLineRunner;

final class CompileJs extends Step {

  public CompileJs(
      OptionsIngredient<JsOptions> optionsIng,
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
  public void execute(Log log) throws MojoExecutionException {
    OptionsIngredient<JsOptions> optionsIng =
        ((OptionsIngredient<?>) inputs.get(0)).asSuperType(JsOptions.class);
    SerializedObjectIngredient<Modules> modulesIng =
        ((SerializedObjectIngredient<?>) inputs.get(1))
        .asSuperType(Modules.class);
    PathValue jsOutputDir = (PathValue) inputs.get(2);

    Modules modules = modulesIng.getStoredObject().get();
    if (modules.modules.isEmpty()) {
      log.info("Skipping JS compilation -- zero modules");
      return;
    }

    jsOutputDir.value.mkdirs();

    ImmutableList.Builder<String> argvBuilder = ImmutableList.builder();
    optionsIng.getOptions().addArgv(log, argvBuilder);

    argvBuilder.add("--manage_closure_dependencies").add("true");
    argvBuilder.add("--js_output_file")
        .add(jsOutputDir.value.getPath()
            // The %outname% substitution is defined in
            // AbstractCommandLineRunner.
             + File.separator + "compiled-%outname%.js");

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
              runner.setExitCodeReceiver(Functions.constant(null));
              runner.run();
              if (runner.hasErrors()) {
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
      ByteArrayOutputStream bytes = (ByteArrayOutputStream) this.out;

      byte[] byteArray = bytes.toByteArray();
      bytes.reset();

      onLine(
          new StringBuilder()
          .append(prefix)
          .append(new String(byteArray, "UTF-8")));
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
