package com.google.closure.plugin.extract;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.closure.plugin.common.CommonPlanner;
import com.google.closure.plugin.common.Ingredients;
import com.google.closure.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.closure.plugin.common.Ingredients
    .SettableFileSetIngredient;
import com.google.closure.plugin.common.OptionsUtils;
import com.google.closure.plugin.common.SourceFileProperty;
import com.google.closure.plugin.extract
    .ResolvedExtractsList.ResolvedExtract;

/**
 * Adds steps to the common plan to extract
 * {@link Extract#DEFAULT_EXTRACT_SUFFIX_SET source files} from specified
 * dependencies' artifacts.
 */
public final class ExtractPlanner {

  private final CommonPlanner planner;
  private final MavenProject project;
  private final PluginDescriptor pluginDescriptor;

  /** */
  public ExtractPlanner(
      CommonPlanner planner, MavenProject project,
      PluginDescriptor pluginDescriptor) {
    this.planner = planner;
    this.project = project;
    this.pluginDescriptor = pluginDescriptor;
  }

  /** Adds steps to do extraction to the common planner. */
  public void plan(Extracts unpreparedExtracts)
  throws IOException, MojoExecutionException {
    Extracts extracts = OptionsUtils.prepareOne(unpreparedExtracts);

    Ingredients ingredients = planner.ingredients;

    ImmutableList.Builder<Extract> allExtracts = ImmutableList.builder();
    {
      Extract builtinExtract = new Extract();
      builtinExtract.setArtifactId(pluginDescriptor.getArtifactId());
      builtinExtract.setGroupId(pluginDescriptor.getGroupId());
      builtinExtract.setVersion(pluginDescriptor.getVersion());
      builtinExtract.setLoadAsNeeded(true);
      allExtracts.add(builtinExtract);
    }
    allExtracts.addAll(Arrays.asList(extracts.extract));

    // First, hash the relevant configuration parts.
    SerializedObjectIngredient<ExtractsList> extractsList =
        ingredients.serializedObject(
            "partial-extracts.ser", ExtractsList.class);
    extractsList.setStoredObject(new ExtractsList(allExtracts.build()));

    // Second, extract the deps from the project, and hash that.
    SerializedObjectIngredient<ResolvedExtractsList> dependenciesList =
        ingredients.serializedObject(
            "dependencies.ser",
            ResolvedExtractsList.class);
    ImmutableSet.Builder<ResolvedExtract> deps = ImmutableSet.builder();

    for (Artifact a :
         ImmutableList.<Artifact>builder()
             .addAll(project.getDependencyArtifacts())
             .add(pluginDescriptor.getPluginArtifact())
             .build()) {
      File artFile = a.getFile();
      if (artFile != null) {
        deps.add(new ResolvedExtract(
            a.getGroupId(),
            a.getArtifactId(),
            a.getVersion(),
            ImmutableSet.<String>of(),
            ("test".equals(a.getScope())
                ? Sets.immutableEnumSet(SourceFileProperty.TEST_ONLY)
                : ImmutableSet.<SourceFileProperty>of()),
            artFile
            ));
      }
    }
    dependenciesList.setStoredObject(
        new ResolvedExtractsList(deps.build()));

    // Third, compare the two to flesh out partial identifiers.
    SerializedObjectIngredient<ResolvedExtractsList> resolvedExtractsList =
        ingredients.serializedObject(
            "full-extracts.ser",
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
