package com.google.common.html.plugin.extract;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.common.html.plugin.common.Ingredients;
import com.google.common.html.plugin.common.Ingredients.FileIngredient;
import com.google.common.html.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.common.html.plugin.common.Ingredients
    .SettableFileSetIngredient;
import com.google.common.html.plugin.extract.ResolvedExtractsList
    .ResolvedExtract;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.PlanKey;
import com.google.common.html.plugin.plan.Step;
import com.google.common.html.plugin.plan.StepSource;

final class ResolveExtracts extends Step {
  private final Ingredients ingredients;
  private final
  SerializedObjectIngredient<ResolvedExtractsList> resolvedExtractsList;
  private final SettableFileSetIngredient archives;

  ResolveExtracts(
      Ingredients ingredients,

      SerializedObjectIngredient<ExtractsList> extractsList,
      SerializedObjectIngredient<ResolvedExtractsList> dependenciesList,

      SerializedObjectIngredient<ResolvedExtractsList> resolvedExtractsList,
      SettableFileSetIngredient archives) {
    super(
        PlanKey.builder("resolve-extracts").build(),
        ImmutableList.<Ingredient>of(extractsList, dependenciesList),
        ImmutableSet.<StepSource>of(),
        ImmutableSet.<StepSource>of());
    this.ingredients = ingredients;
    this.resolvedExtractsList = resolvedExtractsList;
    this.archives = archives;
  }

  @Override
  public void execute(Log log) throws MojoExecutionException {
    SerializedObjectIngredient<ExtractsList> extractsList =
        ((SerializedObjectIngredient<?>) inputs.get(0))
        .asSuperType(ExtractsList.class);
    SerializedObjectIngredient<ResolvedExtractsList> dependenciesList =
        ((SerializedObjectIngredient<?>) inputs.get(1))
        .asSuperType(ResolvedExtractsList.class);

    ImmutableList.Builder<ResolvedExtract> resolvedBuilder =
        ImmutableList.builder();

    ImmutableList<Extract> extracts =
        extractsList.getStoredObject().get().extracts;
    if (!extracts.isEmpty()) {
      Multimap<String, ResolvedExtract> depDisambiguation =
          Multimaps.newSetMultimap(
              Maps.<String, Collection<ResolvedExtract>>newLinkedHashMap(),
              new Supplier<Set<ResolvedExtract>>() {
                @Override
                public Set<ResolvedExtract> get() {
                  return Sets.newLinkedHashSet();
                }
              });
      for (ResolvedExtract re
           : dependenciesList.getStoredObject().get().extracts) {
        depDisambiguation.put(":" + re.artifactId, re);
        depDisambiguation.put(re.groupId + ":" + re.artifactId, re);
        depDisambiguation.put(
            re.groupId + ":" + re.artifactId + ":" + re.version, re);
      }
      System.err.println("depDisambiguation=" + depDisambiguation);

      boolean allUnambiguous = true;
      for (Extract e : extracts) {
        Optional<String> grpId = e.getGroupId();
        Optional<String> artId = e.getArtifactId();
        Optional<String> ver = e.getVersion();
        StringBuilder artKey = new StringBuilder();
        if (grpId.isPresent()) { artKey.append(grpId.get()); }
        artKey.append(':');

        if (!artId.isPresent()) {
          log.error("Extract " + e + " is missing groupId");
          allUnambiguous = false;
          continue;
        }
        artKey.append(artId.get());

        if (ver.isPresent()) {
          artKey.append(':').append(ver.get());
        }

        Collection<ResolvedExtract> deps =
            depDisambiguation.get(artKey.toString());
        if (deps.size() == 1) {
          ResolvedExtract solution = deps.iterator().next();
          resolvedBuilder.add(new ResolvedExtract(
              solution.artifactId,
              solution.groupId,
              solution.version,
              e.getSuffixes(),
              solution.isTestScope,
              solution.archive
              ));
        } else {
          log.error(
              "Extract " + artKey +
              (deps.isEmpty()
               ? " matches no dependencies"
               : " is ambiguous : " + deps));
          allUnambiguous = false;
        }
      }

      if (!allUnambiguous) {
        throw new MojoExecutionException("Not all extracts are unambiguous");
      }
    }

    ImmutableList<ResolvedExtract> resolved = resolvedBuilder.build();
    resolvedExtractsList.setStoredObject(new ResolvedExtractsList(resolved));
    try {
      resolvedExtractsList.write();
    } catch (IOException ex) {
      throw new MojoExecutionException("failed to store resolved extracts", ex);
    }

    buildResolvedExtractsList(resolved);
  }

  private void buildResolvedExtractsList(
      ImmutableList<ResolvedExtract> resolved)
  throws MojoExecutionException {

    ImmutableList.Builder<FileIngredient> mainArchives =
        ImmutableList.builder();
    ImmutableList.Builder<FileIngredient> testArchives =
        ImmutableList.builder();
    try {
      for (ResolvedExtract re : resolved) {
        (re.isTestScope ? testArchives : mainArchives).add(
            ingredients.file(re.archive));
      }
      archives.setFiles(mainArchives.build(), testArchives.build());
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to build hashable file list of archives",
          ex);
    }
  }

  @Override
  public void skip(Log log) throws MojoExecutionException {
    try {
      resolvedExtractsList.read();
    } catch (IOException ex) {
      throw new MojoExecutionException("failed to load resolved extracts", ex);
    }

    buildResolvedExtractsList(
        resolvedExtractsList.getStoredObject().get().extracts);
  }

  @Override
  public ImmutableList<Step> extraSteps(Log log) throws MojoExecutionException {
    return ImmutableList.of();
  }

}
