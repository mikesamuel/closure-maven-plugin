package com.google.closure.plugin.extract;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
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
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.closure.plugin.common.CStyleLexer;
import com.google.closure.plugin.common.FileExt;
import com.google.closure.plugin.common.GenfilesDirs;
import com.google.closure.plugin.common.SourceFileProperty;
import com.google.closure.plugin.extract.ResolvedExtractsList
    .ResolvedExtract;
import com.google.closure.plugin.plan.Hash;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;
import com.google.closure.plugin.proto.ProtoPackageMap;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

final class ExtractFiles extends PlanGraphNode<ExtractFiles.SV> {

  final ResolvedExtractsList resolvedExtractsList;
  /** We need to re-extract if files are mucked with. */
  private SortedSet<File> extractedFiles = Sets.newTreeSet();
  /** Hash of archives so we know whether we need to re-extract. */
  private Map<File, Hash> archiveHash = Maps.newLinkedHashMap();


  ExtractFiles(
      PlanContext context,
      ResolvedExtractsList resolvedExtractsList) {
    super(context);
    this.resolvedExtractsList = resolvedExtractsList;
  }

  @Override
  protected void processInputs() throws IOException, MojoExecutionException {
    archiveHash.clear();
    extractedFiles.clear();
    for (ResolvedExtract e : resolvedExtractsList.extracts) {
      try {
        extract(e);
      } catch (IOException ex) {
        throw new MojoExecutionException(
            "Failed to extract " + e.groupId + ":" + e.artifactId,
            ex);
      }
    }
  }

  private void extract(ResolvedExtract e) throws IOException {
    GenfilesDirs gd = context.genfilesDirs;
    Log log = context.log;

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
                outFile.getParentFile().mkdirs();

                File tmpFile = File.createTempFile("extract", ext);
                Files.write(bytes, tmpFile);
                if (outFile.exists() && Files.equal(outFile, tmpFile)) {
                  // Don't generate unnecessary churn in timestamps or
                  // file-system watcher.
                  tmpFile.delete();
                } else {
                  log.debug(
                      "Extracting " + e.groupId + ":" + e.artifactId
                      + " : " + name + " to " + outFile);
                  // The nio.file version works across physical partitions
                  java.nio.file.Files.move(
                      tmpFile.toPath(), outFile.toPath(),
                      StandardCopyOption.REPLACE_EXISTING);
                  this.extractedFiles.add(outFile);
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

  static final class SV implements PlanGraphNode.StateVector {
    final ResolvedExtractsList resolvedExtractsList;
    /** We need to re-extract if files are mucked with. */
    final ImmutableSortedSet<File> extractedFiles;
    /** Hash of archives so we know whether we need to re-extract. */
    final ImmutableMap<File, Hash> archiveHash;

    private static final long serialVersionUID = -7963994192492879020L;

    SV(ResolvedExtractsList resolvedExtractsList,
       SortedSet<File> extractedFiles,
       Map<File, Hash> archiveHash) {
      this.resolvedExtractsList = resolvedExtractsList;
      this.extractedFiles = ImmutableSortedSet.copyOfSorted(extractedFiles);
      this.archiveHash = ImmutableMap.copyOf(archiveHash);
    }

    @SuppressWarnings("synthetic-access")
    @Override
    public ExtractFiles reconstitute(PlanContext context, JoinNodes jn) {
      ExtractFiles ef = new ExtractFiles(context, resolvedExtractsList);
      ef.archiveHash.putAll(archiveHash);
      ef.extractedFiles.addAll(extractedFiles);
      return ef;
    }

  }

  @Override
  protected boolean hasChangedInputs() throws IOException {
    return true;  // TODO: we could hash jars
  }

  static ImmutableSortedSet<FileExt> extensionsFor(
      ResolvedExtractsList extracts) {
    ImmutableSortedSet.Builder<FileExt> b = ImmutableSortedSet.naturalOrder();
    for (ResolvedExtract e : extracts.extracts) {
      for (String suffix : e.suffixes) {
        Optional<FileExt> ext = FileExt.forFile(suffix);
        if (ext.isPresent()) {
          b.add(ext.get());
        }
      }
    }
    return b.build();
  }

  static ImmutableSortedSet<FileExt> extensionsFor(Extracts extracts) {
    ImmutableSortedSet.Builder<FileExt> b = ImmutableSortedSet.naturalOrder();
    for (Extract e : extracts.getExtracts()) {
      for (String suffix : e.getSuffixes()) {
        Optional<FileExt> ext = FileExt.forFile(suffix);
        if (ext.isPresent()) {
          b.add(ext.get());
        }
      }
    }
    return b.build();
  }

  @Override
  protected
  Optional<ImmutableList<PlanGraphNode<?>>> rebuildFollowersList(JoinNodes jn) {
    return Optional.of(jn.followersOf(extensionsFor(resolvedExtractsList)));
  }

  @Override
  protected void markOutputs() {
    for (File f : this.extractedFiles) {
      context.buildContext.refresh(f);
    }
  }

  @Override
  protected SV getStateVector() {
    return new SV(resolvedExtractsList, extractedFiles, archiveHash);
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
