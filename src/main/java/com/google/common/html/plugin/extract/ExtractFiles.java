package com.google.common.html.plugin.extract;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.html.plugin.common.GenfilesDirs;
import com.google.common.html.plugin.common.Ingredients.SerializedObjectIngredient;
import com.google.common.html.plugin.common.Ingredients.SettableFileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.StringValue;
import com.google.common.html.plugin.extract.ResolvedExtractsList.ResolvedExtract;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.Step;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

final class ExtractFiles extends Step {

  ExtractFiles(
      SerializedObjectIngredient<ResolvedExtractsList> resolvedExtractsList,
      SerializedObjectIngredient<GenfilesDirs> genfiles,
      SettableFileSetIngredient archives,
      StringValue outputDirPath) {
    super(
        "extract-files",
        ImmutableList.<Ingredient>of(
            resolvedExtractsList, genfiles, archives, outputDirPath),
        ImmutableList.<Ingredient>of());
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    SerializedObjectIngredient<ResolvedExtractsList> resolvedExtractsList =
        ((SerializedObjectIngredient<?>) inputs.get(0))
        .asSuperType(ResolvedExtractsList.class);
    SerializedObjectIngredient<GenfilesDirs> genfiles =
        ((SerializedObjectIngredient<?>) inputs.get(1))
        .asSuperType(GenfilesDirs.class);
    StringValue outputDirPath = (StringValue) inputs.get(3);

    GenfilesDirs gf = genfiles.getStoredObject().get();
    File outputDir = new File(outputDirPath.value);

    for (ResolvedExtract e
        : resolvedExtractsList.getStoredObject().get().extracts) {
      try {
        extract(outputDir, gf, e, log);
      } catch (IOException ex) {
        throw new MojoExecutionException(
            "Failed to extract " + e.groupId + ":" + e.artifactId,
            ex);
      }
    }
  }

  @Override
  public void skip(Log log) throws MojoExecutionException {
    // All done.
  }

  @Override
  public ImmutableList<Step> extraSteps(Log log) throws MojoExecutionException {
    return ImmutableList.of();
  }

  private static void extract(
      File outputDir, GenfilesDirs gd, ResolvedExtract e, Log log)
  throws IOException {
    InputStream in = new FileInputStream(e.archive);
    try {
      ZipInputStream zipIn = new ZipInputStream(in);
      try {
        for (ZipEntry entry; (entry = zipIn.getNextEntry()) != null;) {
          if (!entry.isDirectory()) {
            String name = entry.getName();
            if (e.suffixes.contains(FilenameUtils.getExtension(name))) {
              // Suffix matches.
              byte[] bytes = ByteStreams.toByteArray(zipIn);
              File extractedLocation =
                  locationFor(outputDir, gd, name, e.isTestScope, bytes);
              log.debug(
                  "Extracting " + e.groupId + ":" + e.artifactId + " : " + name
                  + " to " + extractedLocation);
              Files.write(bytes, extractedLocation);
            }
          }
          zipIn.closeEntry();
        }
      } finally {
        zipIn.close();
      }
    } finally {
      in.close();
    }
  }

  private static File locationFor(
      File outputDir, GenfilesDirs gd,
      String name, boolean isTestScope, byte[] content) {
    String suffix = FilenameUtils.getExtension(name);
    PathChooser pathChooser = EXTENSION_TO_PATH_CHOOSER.get(suffix);
    if (pathChooser == null) {
      pathChooser = new DefaultPathChooser();
    }
    String relPath = pathChooser.chooseRelativePath(name, content);
    File base;
    if ("js".equals(suffix)) {
      base = isTestScope ? gd.jsTestGenfiles : gd.jsGenfiles;
    } else if ("java".equals(suffix)) {  // Should not be reached
      base = isTestScope ? gd.javaTestGenfiles : gd.javaGenfiles;
    } else {
      // suffix of css -> target/src/main/css
      base = new File(
          new File(
              new File(outputDir, "src"),
              (isTestScope ? "test" : "main")),
          suffix);
    }

    return new File(FilenameUtils.concat(
        base.getPath(),
        relPath.replace('/', File.separatorChar)));
  }


  private static final
  ImmutableMap<String, PathChooser> EXTENSION_TO_PATH_CHOOSER =
      ImmutableMap.<String, PathChooser>of("proto", new FindProtoPackageStmt());
}

interface PathChooser {
  String chooseRelativePath(String entryName, byte[] content);
}

final class DefaultPathChooser implements PathChooser {
  public String chooseRelativePath(String entryName, byte[] content) {
    return entryName;
  }
}

final class FindProtoPackageStmt implements PathChooser {
  public String chooseRelativePath(String entryName, byte[] content) {
    String packageName = null;

    CStyleLexer lex = new CStyleLexer(new String(content, Charsets.UTF_8));

    Iterator<CStyleLexer.Token> it = lex.iterator();
    while (it.hasNext()) {
      CStyleLexer.Token t = it.next();
      if (t.type == CStyleLexer.TokenType.WORD && t.hasText("package")) {
        if (it.hasNext()) {
          t = it.next();
          if (t.type == CStyleLexer.TokenType.WORD) {
            StringBuilder sb = new StringBuilder(t.toString());
            while (it.hasNext() && it.next().hasText(".")) {
              if (it.hasNext()) {
                t = it.next();
                if (t.type == CStyleLexer.TokenType.WORD) {
                  sb.append('/').append(t.toString());
                } else {
                  sb.setLength(0);
                  break;
                }
              }
            }
            packageName = sb.toString();
          }
        }
      }
    }

    if (packageName == null) {
      return entryName;
    }
    int lastSlash = entryName.lastIndexOf('/');
    return packageName.replace('.', '/')
        + "/" + entryName.substring(lastSlash + 1);
  }
}
