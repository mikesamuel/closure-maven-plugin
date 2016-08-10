package com.google.common.html.plugin.css;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.css.ExitCodeHandler;
import com.google.common.css.JobDescription;
import com.google.common.css.SubstitutionMapProvider;
import com.google.common.css.compiler.ast.ErrorManager;
import com.google.common.css.compiler.ast.GssError;
import com.google.common.css.compiler.commandline.ClosureCommandLineCompiler;
import com.google.common.html.plugin.common.Sources;
import com.google.common.io.Files;

final class CssCompilerWrapper {
  private CssOptions cssOptions = new CssOptions();
  private ImmutableList<Sources.Source> inputs = ImmutableList.of();
  private Optional<File> outputFile = Optional.absent();
  private Optional<File> renameFile = Optional.absent();
  private Optional<File> sourceMapFile = Optional.absent();
  private SubstitutionMapProvider substitutionMapProvider;

  CssCompilerWrapper cssOptions(CssOptions newCssOptions) {
    this.cssOptions = newCssOptions;
    return this;
  }
  CssCompilerWrapper inputs(Iterable<? extends Sources.Source> newInputs) {
    this.inputs = ImmutableList.copyOf(newInputs);
    return this;
  }
  CssCompilerWrapper outputFile(File newOutputFile) {
    this.outputFile = Optional.of(newOutputFile);
    return this;
  }
  CssCompilerWrapper renameFile(File newRenameFile) {
    this.renameFile = Optional.of(newRenameFile);
    return this;
  }
  CssCompilerWrapper substitutionMapProvider(
      SubstitutionMapProvider newSubstitutionMapProvider) {
    this.substitutionMapProvider = newSubstitutionMapProvider;
    return this;
  }
  CssCompilerWrapper sourceMapFile(File newSourceMapFile) {
    this.sourceMapFile = Optional.of(newSourceMapFile);
    return this;
  }

  boolean compileCss(final Log log) throws IOException {
    if (inputs.isEmpty()) {
      log.info("No CSS files to compile");
      return true;
    }
    log.info("Compiling " + inputs.size() + " CSS files" +
        (outputFile.isPresent() ? " to " + outputFile.get().getPath() : ""));

    JobDescription job = cssOptions.getJobDescription(
        log, inputs, substitutionMapProvider);

    final class OkUnlessNonzeroExitCodeHandler implements ExitCodeHandler {
      boolean ok = true;

      @Override
      public void processExitCode(int exitCode) {
        if (exitCode != 0) {
          ok = false;
        }
      }
    }

    OkUnlessNonzeroExitCodeHandler exitCodeHandler =
        new OkUnlessNonzeroExitCodeHandler();

    ErrorManager errorManager = new MavenCssErrorManager(log);

    String compiledCss =
        new ClosureCommandLineCompiler(job, exitCodeHandler, errorManager)
        .execute(renameFile.orNull(), sourceMapFile.orNull());
    if (compiledCss == null) {
      return false;
    }
    if (outputFile.isPresent()) {
      Files.write(compiledCss, outputFile.get(), Charsets.UTF_8);
    }
    return exitCodeHandler.ok;
  }
}

final class MavenCssErrorManager implements ErrorManager {
  private boolean hasErrors;
  private final Log log;

  MavenCssErrorManager(Log log) {
    this.log = log;
  }

  @Override
  public void report(GssError error) {
    hasErrors = true;
    log.error(error.format());
  }

  @Override
  public void reportWarning(GssError warning) {
    log.warn(warning.format());
  }

  @Override
  public void generateReport() {
    // Errors reported eagerly.
    // TODO(summarize something)?
  }

  @Override
  public boolean hasErrors() {
    return hasErrors;
  }
}
