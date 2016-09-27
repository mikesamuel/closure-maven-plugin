package com.google.closure.plugin.extract;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.closure.plugin.common.SourceFileProperty;
import com.google.closure.plugin.extract.ResolvedExtractsList.ResolvedExtract;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;

final class ResolveExtracts extends PlanGraphNode<ResolveExtracts.SV> {
  final Extracts options;
  private Optional<ResolvedExtractsList> resolvedExtracts = Optional.absent();

  ResolveExtracts(PlanContext context, Extracts options) {
    super(context);
    this.options = options;
  }

  @Override
  protected void processInputs() throws IOException, MojoExecutionException {
    ImmutableList.Builder<ResolvedExtract> resolvedBuilder =
        ImmutableList.builder();

    ResolvedExtractsList allDependencies;
    {
      ImmutableList.Builder<ResolvedExtract> deps =
          ImmutableList.builder();
      for (Artifact a : context.artifacts) {
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
      allDependencies = new ResolvedExtractsList(deps.build());
    }

    ImmutableList<Extract> extracts = this.options.getExtracts();
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
      for (ResolvedExtract re : allDependencies.extracts) {
        depDisambiguation.put(":" + re.artifactId, re);
        depDisambiguation.put(re.groupId + ":" + re.artifactId, re);
        depDisambiguation.put(
            re.groupId + ":" + re.artifactId + ":" + re.version, re);
      }

      boolean allUnambiguous = true;
      for (Extract e : extracts) {
        Optional<String> grpId = e.getGroupId();
        Optional<String> artId = e.getArtifactId();
        Optional<String> ver = e.getVersion();
        StringBuilder artKey = new StringBuilder();
        if (grpId.isPresent()) { artKey.append(grpId.get()); }
        artKey.append(':');

        if (!artId.isPresent()) {
          context.log.error("Extract " + e + " is missing groupId");
          allUnambiguous = false;
          continue;
        }
        artKey.append(artId.get());

        if (ver.isPresent()) {
          artKey.append(':').append(ver.get());
        }

        Collection<ResolvedExtract> depsForKey =
            depDisambiguation.get(artKey.toString());
        if (depsForKey.size() == 1) {
          ResolvedExtract solution = depsForKey.iterator().next();
          resolvedBuilder.add(new ResolvedExtract(
              solution.artifactId,
              solution.groupId,
              solution.version,
              e.getSuffixes(),
              e.getFileProperties(),
              solution.archive
              ));
        } else {
          context.log.error(
              "Extract " + artKey +
              (depsForKey.isEmpty()
               ? " matches no dependencies"
               : " is ambiguous : " + depsForKey));
          allUnambiguous = false;
        }
      }

      if (!allUnambiguous) {
        throw new MojoExecutionException("Not all extracts are unambiguous");
      }
    }

    ImmutableList<ResolvedExtract> resolved = resolvedBuilder.build();
    this.resolvedExtracts = Optional.of(new ResolvedExtractsList(resolved));
  }

  static final class SV implements PlanGraphNode.StateVector {

    private static final long serialVersionUID = -1303239773246232100L;

    final Extracts options;

    SV(Extracts options) {
      this.options = options;
    }

    @Override
    public ResolveExtracts reconstitute(PlanContext context, JoinNodes jn) {
      return new ResolveExtracts(context, options);
    }
  }


  @Override
  protected boolean hasChangedInputs() throws IOException {
    return true;
  }

  @Override
  protected
  Optional<ImmutableList<ExtractFiles>> rebuildFollowersList(JoinNodes jn) {
    Preconditions.checkState(this.resolvedExtracts.isPresent());
    ImmutableList<PlanGraphNode<?>> node = this.getFollowerList();
    if (node.size() == 1) {
      ExtractFiles ef = (ExtractFiles) node.get(0);
      if (ef.resolvedExtractsList.equals(this.resolvedExtracts.get())) {
        return Optional.absent();
      }
    }
    return Optional.of(
        ImmutableList.of(
            new ExtractFiles(
                context, this.resolvedExtracts.get())
            ));
  }

  @Override
  protected void markOutputs() {
    // Done.
  }

  @Override
  protected SV getStateVector() {
    return new SV(options);
  }
}
