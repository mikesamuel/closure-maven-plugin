package com.google.common.html.plugin.js;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.html.plugin.common.Ingredients
    .DirScanFileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.FileIngredient;
import com.google.common.html.plugin.common.Ingredients.HashedInMemory;
import com.google.common.html.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.common.html.plugin.common.Sources.Source;
import com.google.common.html.plugin.js.Identifier.GoogNamespace;
import com.google.common.html.plugin.js.JsDepInfo.DepInfo;
import com.google.common.html.plugin.plan.FileMetadataMapBuilder;
import com.google.common.html.plugin.plan.FileMetadataMapBuilder.Extractor;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.Metadata;
import com.google.common.html.plugin.plan.PlanKey;
import com.google.common.html.plugin.plan.Step;
import com.google.common.html.plugin.plan.StepSource;
import com.google.common.io.ByteSource;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.SourceFile;

/**
 * Updates a mapping of JS sources to requires and provides while avoiding
 * parsing JS sources as much as possible.
 */
final class ComputeJsDepInfo extends Step {
  private final SerializedObjectIngredient<JsDepInfo> depInfoIng;

  ComputeJsDepInfo(
      HashedInMemory<JsOptions> options,
      SerializedObjectIngredient<JsDepInfo> depInfoIng,
      DirScanFileSetIngredient fs) {
    super(
        PlanKey.builder("compute-js-dep-info")
            .addInp(options, depInfoIng, fs)
            .build(),
        ImmutableList.<Ingredient>of(options, fs),
        Sets.immutableEnumSet(StepSource.JS_GENERATED, StepSource.JS_SRC),
        Sets.immutableEnumSet(StepSource.JS_DEP_INFO));
    this.depInfoIng = depInfoIng;
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    try {
      depInfoIng.read();
    } catch (@SuppressWarnings("unused") FileNotFoundException ex) {
      // Ok.  Rebuild from scratch.
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to read dependency info", ex);
    }

    HashedInMemory<JsOptions> optionsIng =
        ((HashedInMemory<?>) inputs.get(0)).asSuperType(JsOptions.class);
    DirScanFileSetIngredient fs = (DirScanFileSetIngredient) inputs.get(1);

    JsOptions options = optionsIng.getValue();

    Optional<JsDepInfo> depInfoOpt = depInfoIng.getStoredObject();
    ImmutableMap<File, Metadata<DepInfo>> oldDepInfoMap =
        depInfoOpt.isPresent()
        ? depInfoOpt.get().depinfo
        : ImmutableMap.<File, Metadata<DepInfo>>of();

    Iterable<? extends Source> sources = Lists.transform(
        fs.sources(), FileIngredient.GET_SOURCE);

    ImmutableMap<File, Metadata<DepInfo>> newDepInfo;
    try {
      newDepInfo = computeDepInfo(
          log, oldDepInfoMap, options,
          FileMetadataMapBuilder.REAL_FILE_LOADER,
          sources);
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to extract dependency info", ex);
    }

    depInfoIng.setStoredObject(new JsDepInfo(newDepInfo));
    try {
      depInfoIng.write();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to read dependency info", ex);
    }
  }

  @VisibleForTesting
  static ImmutableMap<File, Metadata<DepInfo>> computeDepInfo(
      Log log,
      ImmutableMap<File, Metadata<DepInfo>> oldDepInfoMap,
      JsOptions options,
      Function<Source, ByteSource> loader,
      Iterable<? extends Source> sources)
  throws IOException {
    final Compiler parsingCompiler = new Compiler(
        new MavenLogJSErrorManager(log));
    parsingCompiler.initOptions(options.toCompilerOptions());

    return FileMetadataMapBuilder.updateFromSources(
        oldDepInfoMap,
        loader,
        new Extractor<DepInfo>() {
          @Override
          public DepInfo extractMetadata(Source source, byte[] content)
          throws IOException {
            String code = new String(content, Charsets.UTF_8);

            SourceFile sourceFile = new SourceFile.Builder()
                .withCharset(Charsets.UTF_8)
                .withOriginalPath(source.relativePath.getPath())
                .buildFromCode(source.canonicalPath.getPath(), code);

            CompilerInput inp = new CompilerInput(sourceFile);
            inp.setCompiler(parsingCompiler);

            return new DepInfo(
                inp.isModule(),
                inp.getName(),
                googNamespaces(inp.getProvides()),
                googNamespaces(inp.getRequires()));
          }
        },
        sources);
  }

  @Override
  public void skip(Log log) throws MojoExecutionException {
    try {
      depInfoIng.read();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to read dependency info", ex);
    }
  }

  @Override
  public ImmutableList<Step> extraSteps(Log log) throws MojoExecutionException {
    return ImmutableList.of();
  }

  private static final Function<String, Identifier.GoogNamespace> TO_GOOG_NS =
      new Function<String, Identifier.GoogNamespace>() {

        @Override
        public GoogNamespace apply(String symbolText) {
          return new GoogNamespace(symbolText);
        }

      };

  static Iterable<Identifier.GoogNamespace> googNamespaces(
      Iterable<? extends String> symbols) {
    return Iterables.transform(symbols, TO_GOOG_NS);
  }
}
