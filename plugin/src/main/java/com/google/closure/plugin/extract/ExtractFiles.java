package com.google.closure.plugin.extract;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.closure.plugin.common.CStyleLexer;
import com.google.closure.plugin.common.FileExt;
import com.google.closure.plugin.common.GenfilesDirs;
import com.google.closure.plugin.common.SourceFileProperty;
import com.google.closure.plugin.extract.ResolvedExtractsList
    .ResolvedExtract;
import com.google.closure.plugin.plan.BundlingPlanGraphNode.OptionsAndBundles;
import com.google.closure.plugin.plan.CompilePlanGraphNode;
import com.google.closure.plugin.plan.Hash;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.proto.ProtoPackageMap;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

final class ExtractFiles
extends CompilePlanGraphNode<Extracts, ResolvedExtract> {

  /** Hash of archives so we know whether we need to re-extract. */
  private Map<File, Hash> archiveHash = Maps.newLinkedHashMap();

  ExtractFiles(PlanContext context) {
    super(context);
  }

  @Override
  protected void process() throws IOException, MojoExecutionException {

    // Since changed vs. unchanged vs. defunct is triaged based on input files
    // and dependencies are not part of the project, everything ends up in
    // a single unchanged/changed bundle (depending on whether the build is
    // incremental) associated with an empty input list.

    // For this reason, we don't retain entries for unchanged artifacts in
    // the archive hash.
    // We just process everything and rehash.
    // We also don't clear the archiveHash.  Instead of passing in an old copy
    // we just modify in place.

    // TODO: some way to find defunct entries and remove files extracted from
    // those.

    this.changedFiles.clear();
    this.processDefunctBundles(this.optionsAndBundles);

    for (OptionsAndBundles<Extracts, ResolvedExtract> ob
         : this.optionsAndBundles.get().allExtant()) {
      for (ResolvedExtract e : ob.bundles) {
        processOne(e);
      }
    }
  }

  protected void processOne(ResolvedExtract e)
  throws MojoExecutionException {
    try {
      extract(e);
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to extract " + e.groupId + ":" + e.artifactId,
          ex);
    }
  }

  private void extract(ResolvedExtract e) throws IOException {
    GenfilesDirs gd = context.genfilesDirs;
    Log log = context.log;

    ImmutableList.Builder<File> filesForBundle = ImmutableList.builder();
    byte[] archiveBytes = Files.toByteArray(e.archive);
    try (InputStream in = new ByteArrayInputStream(archiveBytes)) {
      try (ZipInputStream zipIn = new ZipInputStream(in)) {
        for (ZipEntry entry; (entry = zipIn.getNextEntry()) != null;) {
          if (!entry.isDirectory()) {
            String name = entry.getName();
            String ext = FilenameUtils.getExtension(name);
            if (e.suffixes.contains(ext)) {
              // Suffix matches.
              byte[] bytes = ByteStreams.toByteArray(zipIn);
              Optional<File> extractedLocation = locationFor(
                  gd, name, e.props, bytes);
              if (extractedLocation.isPresent()) {
                File outFile = extractedLocation.get();
                Files.createParentDirs(outFile);
                filesForBundle.add(outFile);

                File tmpFile = File.createTempFile("extract", ext);
                Files.write(bytes, tmpFile);
                if (outFile.exists() && Files.equal(outFile, tmpFile)) {
                  // Don't generate unnecessary churn in timestamps or
                  // file-system watcher by copying equivalent content into a
                  // file.
                  @SuppressWarnings("unused")
                  boolean deleted = tmpFile.delete();  // best effort
                } else {
                  log.debug(
                      "Extracting " + e.groupId + ":" + e.artifactId
                      + " : " + name + " to " + outFile);
                  // The nio.file version works across physical partitions
                  java.nio.file.Files.move(
                      tmpFile.toPath(), outFile.toPath(),
                      StandardCopyOption.REPLACE_EXISTING);
                  this.changedFiles.add(outFile);
                }
              } else {
                log.warn("Cannot find location for extract " + name);
              }
            }
          }
          zipIn.closeEntry();
        }
      }
    }
    this.archiveHash.put(e.archive, Hash.hashBytes(archiveBytes));
    this.bundleToOutputs.put(e, filesForBundle.build());
  }

  private static Optional<File> locationFor(
      GenfilesDirs gd, String name, Set<SourceFileProperty> props,
      byte[] content) {
    Optional<FileExt> extOpt = FileExt.forFile(name);
    if (!extOpt.isPresent()) {
      return Optional.absent();
    }
    FileExt ext = extOpt.get();
    PathChooser pathChooser = EXTENSION_TO_PATH_CHOOSER.get(ext);
    if (pathChooser == null) {
      pathChooser = DefaultPathChooser.INSTANCE;
    }
    String relPath = pathChooser.chooseRelativePath(name, content);
    File base = gd.getGeneratedSourceDirectory(ext, props);

    return Optional.of(new File(FilenameUtils.concat(
        base.getPath(),
        relPath.replace('/', File.separatorChar))));
  }


  private static final
  ImmutableMap<FileExt, PathChooser> EXTENSION_TO_PATH_CHOOSER =
      ImmutableMap.<FileExt, PathChooser>of(
          FileExt.PROTO, new FindProtoPackageStmt());

  static final class SV
  extends CompilePlanGraphNode.CompileStateVector<Extracts, ResolvedExtract> {
    /** Hash of archives so we know whether we need to re-extract. */
    final ImmutableMap<File, Hash> archiveHash;

    private static final long serialVersionUID = 1L;

    @SuppressWarnings("synthetic-access")
    SV(ExtractFiles node) {
      super(node);
      this.archiveHash = ImmutableMap.copyOf(node.archiveHash);
    }

    @SuppressWarnings("synthetic-access")
    @Override
    public ExtractFiles reconstitute(PlanContext context, JoinNodes jn) {
      ExtractFiles ef = apply(new ExtractFiles(context));
      ef.archiveHash.putAll(archiveHash);
      return ef;
    }

  }

  @Override
  protected SV getStateVector() {
    return new SV(this);
  }
}

interface PathChooser {
  String chooseRelativePath(String entryName, byte[] content);
}

final class DefaultPathChooser implements PathChooser {
  static final DefaultPathChooser INSTANCE = new DefaultPathChooser();

  private static final Pattern PREFIX = Pattern.compile(
      "^(src|dep)/(main|test)/(\\w+)/");

  @Override
  public String chooseRelativePath(String entryName, byte[] content) {
    // Strip {src,dep}/{main,test}/ext from the front.
    String path = entryName.replace('/', File.separatorChar);
    path = Files.simplifyPath(path);
    path = path.replace(File.separatorChar, '/');
    Matcher matcher = PREFIX.matcher(path);
    if (matcher.find()) {
      path = path.substring(matcher.end());
    }
    return path;
  }
}

final class FindProtoPackageStmt implements PathChooser {
  @Override
  public String chooseRelativePath(String entryName, byte[] content) {

    CStyleLexer lex = new CStyleLexer(new String(content, Charsets.UTF_8));

    Optional<String> packageName = ProtoPackageMap.getPackage(lex);

    if (!packageName.isPresent()) {
      return new DefaultPathChooser().chooseRelativePath(entryName, content);
    }
    int lastSlash = entryName.lastIndexOf('/');
    return packageName.get().replace('.', '/')
        + "/" + entryName.substring(lastSlash + 1);
  }
}
