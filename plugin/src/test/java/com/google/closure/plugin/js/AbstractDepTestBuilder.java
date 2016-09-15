package com.google.closure.plugin.js;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.closure.plugin.TestLog;
import com.google.closure.plugin.common.OptionsUtils;
import com.google.closure.plugin.common.SourceFileProperty;
import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.common.TypedFile;
import com.google.closure.plugin.js.JsDepInfo.DepInfo;
import com.google.closure.plugin.plan.Metadata;
import com.google.common.io.ByteSource;

abstract class AbstractDepTestBuilder<T extends AbstractDepTestBuilder<T>> {
  private final Map<String, String> fileContent = Maps.newLinkedHashMap();
  private final ImmutableList.Builder<Source> sources = ImmutableList.builder();
  private Log log = new TestLog();

  private final Class<T> typ;

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
  throws MojoExecutionException;

  void run() throws IOException, MojoExecutionException {
    ImmutableList<Source> sourceList = this.sources.build();
    JsOptions options = OptionsUtils.prepareOne(new JsOptions());
    JsDepInfo depInfo = new JsDepInfo(ComputeJsDepInfo.computeDepInfo(
        log, ImmutableMap.<File, Metadata<DepInfo>>of(),
        options,
        new Function<Source, ByteSource>() {
          @SuppressWarnings("synthetic-access")
          @Override
          public ByteSource apply(Source s) {
            String contentKey = s.canonicalPath.getPath();
            String charContent = fileContent.get(contentKey);
            Preconditions.checkNotNull(charContent, s.canonicalPath);
            return ByteSource.wrap(charContent.getBytes(Charsets.UTF_8));
          }
        },
        sourceList));
    run(log, options, sourceList, depInfo);
  }
}
