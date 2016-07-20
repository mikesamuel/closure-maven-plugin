package com.google.common.html.plugin.extract;

import java.io.File;
import java.io.IOException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.html.plugin.common.CommonPlanner;
import com.google.common.html.plugin.common.Ingredients;
import com.google.common.html.plugin.common.Ingredients.SerializedObjectIngredient;
import com.google.common.html.plugin.common.Ingredients.SettableFileSetIngredient;
import com.google.common.html.plugin.extract.ResolvedExtractsList.ResolvedExtract;

/**
 * Adds steps to the common plan to extract
 * {@link Extract#DEFAULT_EXTRACT_SUFFIX_SET source files} from specified
 * dependencies' artifacts.
 */
public final class ExtractPlanner {

  private final CommonPlanner planner;
  private final MavenProject project;

  /** */
  public ExtractPlanner(CommonPlanner planner, MavenProject project) {
    this.planner = planner;
    this.project = project;
  }

  public void plan(Iterable<? extends Extract> extracts)
  throws IOException, MojoExecutionException {
    File extractsDir = new File(planner.outputDir, "extracts");

    Ingredients ingredients = planner.ingredients;

    // First, hash the relevant configuration parts.
    SerializedObjectIngredient<ExtractsList> extractsList =
        ingredients.serializedObject(
            new File(extractsDir, "partial-extracts.ser"),
            ExtractsList.class);
    extractsList.setStoredObject(new ExtractsList(extracts));

    // Second, extract the deps from the project, and hash that.
    SerializedObjectIngredient<ResolvedExtractsList> dependenciesList =
        ingredients.serializedObject(
            new File(extractsDir, "dependencies.ser"),
            ResolvedExtractsList.class);
    ImmutableList.Builder<ResolvedExtract> deps = ImmutableList.builder();
    for (Artifact a : project.getDependencyArtifacts()) {
      File artFile = a.getFile();
      if (artFile != null) {
        deps.add(new ResolvedExtract(
            a.getGroupId(),
            a.getArtifactId(),
            a.getVersion(),
            ImmutableSet.<String>of(),
            "test".equals(a.getScope()),
            artFile
            ));
      }
    }
    dependenciesList.setStoredObject(new ResolvedExtractsList(deps.build()));

    // Third, compare the two to flesh out partial identifiers.
    SerializedObjectIngredient<ResolvedExtractsList> resolvedExtractsList =
        ingredients.serializedObject(
            new File(extractsDir, "full-extracts.ser"),
            ResolvedExtractsList.class);
    SettableFileSetIngredient archives =
        ingredients.namedFileSet("archives-list");
    planner.addStep(new ResolveExtracts(
        ingredients,
        extractsList, dependenciesList, resolvedExtractsList, archives));

    // Finally, pull the files out.
    planner.addStep(new ExtractFiles(
        resolvedExtractsList, planner.genfiles, archives,
        ingredients.stringValue(planner.outputDir.getPath())));
  }
}
