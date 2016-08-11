package com.google.common.html.plugin.js;

import java.io.File;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.html.plugin.TestLog;
import com.google.common.html.plugin.common.OptionsUtils;
import com.google.common.html.plugin.common.SourceFileProperty;
import com.google.common.html.plugin.common.Sources.Source;
import com.google.common.html.plugin.common.TypedFile;
import com.google.common.html.plugin.plan.Hash;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.SourceFile;

abstract class AbstractDepTestBuilder<T extends AbstractDepTestBuilder<T>> {
  private final Map<String, String> fileContent = Maps.newLinkedHashMap();
  private final ImmutableList.Builder<Source> sources = ImmutableList.builder();
  private Log log = new TestLog();

  private Class<T> typ;

  AbstractDepTestBuilder(Class<T> typ) {
    this.typ = typ;
  }

  private static Source src(
      String root, String relPath, SourceFileProperty... ps) {
    return new Source(
        new File(root + "/" + relPath),
        new TypedFile(new File(root), ps),
        new File(relPath));
  }

  T fileContent(String path, String content) {
    Preconditions.checkState(null == fileContent.put(path, content));
    return typ.cast(this);
  }

  T mainSource(String root, String relPath) {
    sources.add(src(root, relPath));
    return typ.cast(this);
  }

  T testSource(String root, String relPath) {
    sources.add(src(root, relPath, SourceFileProperty.TEST_ONLY));
    return typ.cast(this);
  }

  T source(String root, String relPath, SourceFileProperty... fileProperties) {
    sources.add(src(root, relPath, fileProperties));
    return typ.cast(this);
  }

  Log log() {
    return log;
  }

  T log(Log newLog) {
    this.log = Preconditions.checkNotNull(newLog);
    return typ.cast(this);
  }

  abstract void run(
      Log alog, JsOptions options, ImmutableList<Source> sourceList,
      JsDepInfo depInfo)
  throws MojoExecutionException ;

  void run() throws MojoExecutionException {
    ImmutableList<Source> sourceList = this.sources.build();
    JsOptions options = OptionsUtils.prepareOne(new JsOptions());
    JsDepInfo depInfo = new JsDepInfo(ComputeJsDepInfo.computeDepInfo(
        log, ImmutableMap.<File, JsDepInfo.HashAndDepInfo>of(),
        options,
        new CompilerInputFactory() {
          @Override
          public CompilerInput create(Source source) {
            @SuppressWarnings("synthetic-access")
            String content = Preconditions.checkNotNull(
                fileContent.get(source.canonicalPath.getPath()),
                source.canonicalPath.getPath());
            SourceFile sf = SourceFile.builder()
                .withOriginalPath(source.relativePath.getPath())
                .buildFromCode(source.canonicalPath.getPath(), content);
            return new CompilerInput(sf);
          }
          @Override
          public Hash hash(Source source) {
            @SuppressWarnings("synthetic-access")
            String content = Preconditions.checkNotNull(
                fileContent.get(source.canonicalPath.getPath()),
                source.canonicalPath.getPath());
            return Hash.hashString(content);
          }
        },
        sourceList));
    run(log, options, sourceList, depInfo);
  }
}
