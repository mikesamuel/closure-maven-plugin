package com.google.closure.plugin.genjava;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Generated;

import org.apache.maven.plugin.MojoExecutionException;
import org.sonatype.plexus.build.incremental.BuildContext;

import com.google.closure.plugin.common.DirectoryScannerSpec;
import com.google.closure.plugin.common.Sources;
import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.common.TypedFile;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;
import com.google.closure.plugin.plan.Update;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import com.google.common.io.Files;

final class GenJavaSymbols extends PlanGraphNode<GenJavaSymbols.SV> {

  final DirectoryScannerSpec outputFileSpec;
  final File webFilesJava;
  final String packageName;
  private Optional<Update<Source>> sourceUpdate = Optional.absent();
  private final List<File> outputFiles = Lists.newArrayList();

  GenJavaSymbols(
      PlanContext context,
      DirectoryScannerSpec outputFileSpec,
      File webFilesJava, String packageName) {
    super(context);
    this.outputFileSpec = outputFileSpec;
    this.webFilesJava = webFilesJava;
    this.packageName = packageName;
  }

  @Override
  protected void preExecute(Iterable<? extends PlanGraphNode<?>> preceders) {
    // Nop
  }

  @Override
  protected void filterUpdates() throws IOException, MojoExecutionException {
    BuildContext bc = context.buildContext;

    ImmutableSet.Builder<Source> unchanged = ImmutableSet.builder();
    ImmutableSet.Builder<Source> changed = ImmutableSet.builder();
    ImmutableSet.Builder<Source> defunct = ImmutableSet.builder();

    if (sourceUpdate.isPresent() && bc.isIncremental()) {
      Set<Source> unchangedSet = Sets.newLinkedHashSet();
      unchangedSet.addAll(sourceUpdate.get().allExtant());
      for (TypedFile root : outputFileSpec.roots) {
        ImmutableList<Source> deltaPaths = outputFileSpec.scan(
            bc.newScanner(root.f, false), root.ps);
        ImmutableList<Source> deletedPaths = outputFileSpec.scan(
            bc.newDeleteScanner(root.f), root.ps);
        changed.addAll(deltaPaths);
        defunct.addAll(deletedPaths);
        unchangedSet.removeAll(deltaPaths);
        unchangedSet.removeAll(deletedPaths);
      }
      unchanged.addAll(unchangedSet);
    } else {
      changed.addAll(Sources.scan(context.log, outputFileSpec).sources);
    }

    this.sourceUpdate = Optional.of(new Update<>(
        unchanged.build(),
        changed.build(),
        defunct.build()));
  }

  @Override
  protected Iterable<? extends File> changedOutputFiles() {
    return ImmutableList.copyOf(outputFiles);
  }

  @Override
  protected void process() throws IOException, MojoExecutionException {
    this.outputFiles.clear();

    if (context.buildContext.isIncremental()
        && !sourceUpdate.get().hasChanges()) {
      return;
    }

    ImmutableSortedSet<Source> buildOutputs = ImmutableSortedSet
        .orderedBy(new Comparator<Source>() {
          @Override
          public int compare(Source a, Source b) {
            int delta = a.root.f.compareTo(b.root.f);
            if (delta == 0) {
              delta = a.relativePath.compareTo(b.relativePath);
            }
            return delta;
          }
        })
        .addAll(sourceUpdate.get().unchanged)
        .addAll(sourceUpdate.get().changed)
        .build();

    Multimap<String, File> constantNameToRelPaths = TreeMultimap.create();
    for (Source s : buildOutputs) {
      File relativePath = s.relativePath;
      String name = bestEffortIdentifier(relativePath);
      constantNameToRelPaths.put(name, relativePath);
    }

    Map<String, String> uniqConstantNameToUriPath = Maps.newLinkedHashMap();
    for (Map.Entry<String, Collection<File>> e
         : constantNameToRelPaths.asMap().entrySet()) {
      String identUnuniq = e.getKey();
      Collection<File> files = e.getValue();
      // If there's one, try to use identUnuniq as-is.
      int index = files.size() == 1 ? -1 : 0;
      for (File relPath : files) {
        String path = relPath.getPath();
        if (File.separatorChar != '/') {
          path = path.replace(File.separatorChar, '/');
        }
        // rel-paths are relative, but we want something
        // that can be appended to a base directory to give an absolute URI
        // path, so start with a "/".
        Preconditions.checkState(!path.startsWith("/"));
        path = "/" + path;
        String identUniq;
        do {
          identUniq = index == -1 ? identUnuniq : identUnuniq + "$" + index;
          ++index;
        } while (uniqConstantNameToUriPath.containsKey(identUniq));
        Object prev = uniqConstantNameToUriPath.put(identUniq, path);
        Preconditions.checkState(prev == null);
      }
    }

    String cName = webFilesJava.getName().replaceFirst("[.]java$", "");
    Preconditions.checkState(cName.indexOf('.') < 0);

    JavaWriter jw = new JavaWriter();
    jw.appendCode("// Generated by ").appendCode(getClass().getName()).nl();
    jw.appendCode("package ").appendCode(packageName).appendCode(";\n");
    jw.nl();
    jw.appendCode("import ")
        .appendCode(Generated.class.getName())
        .appendCode(";\n");
    jw.nl();
    jw.appendCode("/**\n");
    jw.appendCode(" * Symbolic constants for compiled web resources.\n");
    jw.appendCode(" */\n");
    jw.appendCode("@").appendCode(Generated.class.getSimpleName())
        .appendCode("(value=").appendStringLiteral(getClass().getName())
        .appendCode(")\n");
    jw.appendCode("public final class ").appendCode(cName).appendCode(" {\n");
    jw.appendCode(  "private ").appendCode(cName).appendCode("() {\n");
    jw.appendCode(    "// Not instantiable.\n");
    jw.appendCode(  "}\n");
    for (Map.Entry<String, String> e : uniqConstantNameToUriPath.entrySet()) {
      String ident = e.getKey();
      String uriPath = e.getValue();
      String ext = Files.getFileExtension(uriPath);
      jw.nl();
      jw.appendCode("/** Web path to a compiled {@code ")
        .appendCommentPart(ext).appendCode("} file. */\n");
      jw.appendCode("public static final String ").appendCode(ident)
        .appendCode(" = ").appendStringLiteral(uriPath).appendCode(";\n");
    }
    jw.appendCode("}\n");

    try {
      Files.createParentDirs(webFilesJava);
      Files.write(jw.toJava(), webFilesJava, Charsets.UTF_8);
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to write symbols file", ex);
    }
    this.outputFiles.add(webFilesJava);
  }

  private static String bestEffortIdentifier(File f) {
    StringBuilder sb = new StringBuilder();
    appendBestEffortIdentifier(f, sb);
    if (sb.length() == 0
        || !Character.isJavaIdentifierStart(sb.codePointAt(0))) {
      sb.insert(0, '$');
    }
    return sb.toString();
  }

  private static void appendBestEffortIdentifier(File f, StringBuilder sb) {
    File parent = f.getParentFile();
    if (parent != null) {
      appendBestEffortIdentifier(parent, sb);
      sb.append('_');
    }
    String name = f.getName();
    int n = name.length();
    for (int i = 0, cp; i < n; i += Character.charCount(cp)) {
      // By upper-casing we move the identifier out of the space of reserved
      // keywords and follow the Java naming convention for constants.
      cp = Character.toUpperCase(name.codePointAt(i));
      if (Character.isJavaIdentifierPart(cp)) {
        sb.appendCodePoint(cp);
      } else {
        sb.append('_');
      }
    }
  }

  @Override
  protected SV getStateVector() {
    return new SV(outputFileSpec, webFilesJava, packageName);
  }

  static final class SV implements PlanGraphNode.StateVector {
    private static final long serialVersionUID = 1L;

    final DirectoryScannerSpec outputFileSpec;
    final File webFilesJava;
    final String packageName;

    SV(DirectoryScannerSpec outputFileSpec,
       File webFilesJava, String packageName) {
      this.outputFileSpec = outputFileSpec;
      this.webFilesJava = webFilesJava;
      this.packageName = packageName;
    }

    @Override
    public PlanGraphNode<?> reconstitute(PlanContext c, JoinNodes jn) {
      return new GenJavaSymbols(c, outputFileSpec, webFilesJava, packageName);
    }
  }
}
