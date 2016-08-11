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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.html.plugin.common.Ingredients
    .DirScanFileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.FileIngredient;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.common.html.plugin.common.Sources.Source;
import com.google.common.html.plugin.js.Identifier.GoogNamespace;
import com.google.common.html.plugin.plan.Hash;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.PlanKey;
import com.google.common.html.plugin.plan.Step;
import com.google.common.html.plugin.plan.StepSource;
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
      OptionsIngredient<JsOptions> options,
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

    OptionsIngredient<JsOptions> optionsIng =
        ((OptionsIngredient<?>) inputs.get(0)).asSuperType(JsOptions.class);
    DirScanFileSetIngredient fs = (DirScanFileSetIngredient) inputs.get(1);

    JsOptions options = optionsIng.getOptions();

    Optional<JsDepInfo> depInfoOpt = depInfoIng.getStoredObject();
    ImmutableMap<File, JsDepInfo.HashAndDepInfo> oldDepInfoMap =
        depInfoOpt.isPresent()
        ? depInfoOpt.get().depinfo
        : ImmutableMap.<File, JsDepInfo.HashAndDepInfo>of();

    Iterable<? extends Source> sources = Lists.transform(
        fs.sources(), FileIngredient.GET_SOURCE);

    ImmutableMap<File, JsDepInfo.HashAndDepInfo> newDepInfo = computeDepInfo(
        log, oldDepInfoMap, options,
        new CompilerInputFactory() {
          @Override
          public CompilerInput create(Source source) {
            SourceFile sourceFile = new SourceFile.Builder()
                .withCharset(Charsets.UTF_8)
                .withOriginalPath(source.relativePath.getPath())
                .buildFromFile(source.canonicalPath);
            return new CompilerInput(sourceFile);
          }
          @Override
          public Hash hash(Source source) throws IOException {
            return Hash.hash(source);
          }
        },
        sources);

    depInfoIng.setStoredObject(new JsDepInfo(newDepInfo));
    try {
      depInfoIng.write();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to read dependency info", ex);
    }
  }

  @VisibleForTesting
  static ImmutableMap<File, JsDepInfo.HashAndDepInfo> computeDepInfo(
      Log log,
      ImmutableMap<File, JsDepInfo.HashAndDepInfo> oldDepInfoMap,
      JsOptions options,
      CompilerInputFactory ciFactory,
      Iterable<? extends Source> sources)
  throws MojoExecutionException {

    // Create a new hash by either copying entries from the old or recomputing.
    ImmutableMap.Builder<File, JsDepInfo.HashAndDepInfo> newDepInfoBuilder =
        ImmutableMap.builder();

    final Compiler parsingCompiler = new Compiler(
        new MavenLogJSErrorManager(log));
    parsingCompiler.initOptions(options.toCompilerOptions());


    // For each file, check its hash and recompute as appropriate
    for (Source source : sources) {
      File mapKey = source.canonicalPath;
      JsDepInfo.HashAndDepInfo oldMapValue = oldDepInfoMap.get(mapKey);
      Hash oldHash = oldMapValue != null ? oldMapValue.hash : null;
      Hash hash;
      try {
        hash = ciFactory.hash(source);
      } catch (IOException ex) {
        throw new MojoExecutionException(
            "Could not hash " + source.canonicalPath, ex);
      }
      JsDepInfo.HashAndDepInfo newMapValue;
      if (hash.equals(oldHash)) {
        newMapValue = Preconditions.checkNotNull(oldMapValue);
      } else {
        CompilerInput inp = ciFactory.create(source);
        inp.setCompiler(parsingCompiler);

        newMapValue = new JsDepInfo.HashAndDepInfo(
            hash,
            inp.getName(),
            googNamespaces(inp.getProvides()),
            googNamespaces(inp.getRequires()));
      }
      newDepInfoBuilder.put(mapKey, newMapValue);
    }
    return newDepInfoBuilder.build();
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

  private static Iterable<Identifier.GoogNamespace> googNamespaces(
      Iterable<? extends String> symbols) {
    return Iterables.transform(symbols, TO_GOOG_NS);
  }
}
