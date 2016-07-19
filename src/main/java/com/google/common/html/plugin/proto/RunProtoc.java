package com.google.common.html.plugin.proto;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.html.plugin.Sources.Source;
import com.google.common.html.plugin.common.Ingredients
    .DirScanFileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.FileIngredient;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients.StringValue;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.Step;

final class RunProtoc extends Step {
  final RunProtoc.RootSet rootSet;

  RunProtoc(
      RunProtoc.RootSet rootSet,
      OptionsIngredient<ProtoOptions> options,
      DirScanFileSetIngredient protoSources,
      FileIngredient protoc,
      ImmutableList<FileIngredient> inputs,
      StringValue javaGenfilesPath,
      StringValue jsGenfilesPath,
      FileIngredient descriptorSetFile) {
    super(
        // TODO: esc
        "proto-to-java:[" + options.key + "];[" + protoSources.key + "];"
            + rootSet,
        ImmutableList.<Ingredient>builder()
            .add(options, protoSources, protoc,
                 javaGenfilesPath, jsGenfilesPath)
            .addAll(inputs)
            .build(),
        ImmutableList.<Ingredient>of(
            descriptorSetFile));
    this.rootSet = Preconditions.checkNotNull(rootSet);
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    @SuppressWarnings("unused")
    OptionsIngredient<ProtoOptions> options =
        ((OptionsIngredient<?>) inputs.get(0)).asSuperType(ProtoOptions.class);
    DirScanFileSetIngredient protoSources =
        (DirScanFileSetIngredient) inputs.get(1);
    FileIngredient protoc = (FileIngredient) inputs.get(2);
    StringValue javaGenfilesPath = (StringValue) inputs.get(3);
    StringValue jsGenfilesPath = (StringValue) inputs.get(4);
    ImmutableList<FileIngredient> inputProtoFiles = castList(
        inputs.subList(5, inputs.size()), FileIngredient.class);
    FileIngredient descriptorSetFile = (FileIngredient) outputs.get(0);

    Optional<ImmutableList<FileIngredient>> sources = Optional.absent();
    switch (rootSet) {
      case MAIN:
        sources = protoSources.mainSources();
        break;
      case TEST:
        sources = protoSources.testSources();
        break;
    }
    Preconditions.checkState(sources.isPresent());

    ImmutableList.Builder<String> argv = ImmutableList.builder();
    argv.add(protoc.source.canonicalPath.getPath());

    argv.add("--include_imports")
        .add("--descriptor_set_out")
        .add(descriptorSetFile.source.canonicalPath.getPath());

    argv.add("--java_out").add(javaGenfilesPath.value);
    argv.add("--js_out").add(jsGenfilesPath.value);

    // Build a proto search path.
    Set<File> roots = Sets.newLinkedHashSet();
    if (rootSet == RootSet.TEST) {
      for (File root : protoSources.testRoots()) {
        if (roots.add(root)) {
          argv.add("--proto_path").add(root.getPath());
        }
      }
    }
    for (File root : protoSources.mainRoots()) {
      if (roots.add(root)) {
        argv.add("--proto_path").add(root.getPath());
      }
    }

    for (FileIngredient input : inputProtoFiles) {
      File root = input.source.root;
      if (roots.add(root)) {
        argv.add("--proto_path").add(root.getPath());
        // We're not guarding against ambiguity here.
        // We warn on it below.
      }
    }

    // Check for obvious sources of ambiguity due to two inputs with the
    // same relative path.  We pass absolute paths to protoc, but the
    // paths resolved by `import "<relative-path>";` directives are still
    // a potential source of ambiguity.
    Map<File, Source> relPathToSource = Maps.newHashMap();
    for (FileIngredient input : inputProtoFiles) {
      Source inputSource = input.source;
      Source ambig = relPathToSource.put(inputSource.relativePath, inputSource);
      if (ambig == null) {
        argv.add(
            // Instead of using canonicalPath, we concat these two paths
            // because protoc insists that each input appear under a
            // search path element as determined by string comparison.
            FilenameUtils.concat(
                inputSource.root.getPath(),
                inputSource.relativePath.getPath()));
      } else {
        log.warn(
            "Ambiguous proto input " + inputSource.relativePath
            + " appears on search path twice: "
            + ambig.root + " and " + inputSource.root);
      }
    }

    ProcessBuilder protocProcessBuilder = new ProcessBuilder(argv.build())
        .inheritIO();
    if (log.isDebugEnabled()) {
      log.debug("Running protoc: " + protocProcessBuilder.command());
    }
    int exitCode;
    long t0 = System.nanoTime();
    try {
      Process protocProcess = protocProcessBuilder.start();
      protocProcess.waitFor(30, TimeUnit.SECONDS);
      if (protocProcess.isAlive()) {
        log.error("Timed-out waiting for protoc");
        protocProcess.destroyForcibly();
        exitCode = -1;
      } else {
        exitCode = protocProcess.exitValue();
      }
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "process did not complete normally: " + protoc, ex);
    } catch (InterruptedException ex) {
      throw new MojoExecutionException(
          "process did not complete normally: " + protoc, ex);
    }
    long t1 = System.nanoTime();

    String message = "protoc completed with exit code " + exitCode + " after "
        + ((t1 - t0) / (1000000L /* ns / ms */)) + " ms";
    if (exitCode == 0) {
      log.debug(message);  // Yay!
    } else {
      throw new MojoExecutionException(message);
    }
  }

  @Override
  public void skip(Log log) {
    // Ok.
  }

  @Override
  public ImmutableList<Step> extraSteps(Log log) throws MojoExecutionException {
    return ImmutableList.of();
  }

  enum RootSet {
    MAIN,
    TEST,
    ;
  }

  private static <E, T extends E> ImmutableList<T> castList(
      ImmutableList<E> ls, Class<T> elementType) {
    for (E element : ls) {
      elementType.cast(element);
    }
    @SuppressWarnings("unchecked")
    ImmutableList<T> lsAsTList = (ImmutableList<T>) ls;
    return lsAsTList;
  }
}
