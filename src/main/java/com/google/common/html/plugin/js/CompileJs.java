package com.google.common.html.plugin.js;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.json.simple.JSONArray;

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
      PathValue outputDir) {
    super(
        PlanKey.builder("compile-js")
            .addInp(optionsIng, modulesIng, outputDir)
            .build(),
        ImmutableList.<Ingredient>of(optionsIng, modulesIng, outputDir),
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
    PathValue outputDir = (PathValue) inputs.get(2);

    ImmutableList.Builder<String> argvBuilder = ImmutableList.builder();
    optionsIng.getOptions().addArgv(log, argvBuilder);

    argvBuilder.add("--manage_closure_dependencies").add("true");
    argvBuilder.add("--js_output_file").add(outputDir.value.getPath());

    modulesIng.getStoredObject().get().addClosureCompilerFlags(argvBuilder);

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
          new WithStdOutAndStderr() {
            @Override
            void run(PrintStream stdout, PrintStream stderr)
            throws IOException, MojoExecutionException {
              CommandLineRunner runner = new CommandLineRunner(
                  argv.toArray(new String[0]),
                  stdout, stderr) {
                // Subclass to get access to the constructor.
              };
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


  private static void logViaErrStreams(
      String prefix, final Log log, WithStdOutAndStderr withStdOutAndStderr)
  throws IOException, MojoExecutionException {
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
            withStdOutAndStderr.run(out, err);
          }
        }
      }
    }
  }

  abstract class WithStdOutAndStderr {
    abstract void run(PrintStream stdout, PrintStream stderr)
    throws IOException, MojoExecutionException;
  }
}
