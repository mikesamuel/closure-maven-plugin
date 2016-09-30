package com.google.closure.plugin.extract;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;

import com.google.closure.plugin.common.FileExt;
import com.google.closure.plugin.common.OptionsUtils;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;

/**
 * Adds steps to the common plan to extract
 * {@link Extract#DEFAULT_EXTRACT_SUFFIX_SET source files} from specified
 * dependencies' artifacts.
 */
public final class ExtractPlanner {
  final PlanContext context;
  final JoinNodes joinNodes;

  /** */
  public ExtractPlanner(PlanContext context, JoinNodes joinNodes) {
    this.context = context;
    this.joinNodes = joinNodes;
  }

  /** Adds steps to do extraction to the common planner. */
  public void plan(Extracts unpreparedExtracts) throws MojoExecutionException {

    Extracts extracts = OptionsUtils.prepareOne(unpreparedExtracts);
    extracts = extracts.clone();

    {
      PluginDescriptor pluginDescriptor = context.pluginDescriptor;
      Extract builtinExtract = new Extract();
      builtinExtract.setArtifactId(pluginDescriptor.getArtifactId());
      builtinExtract.setGroupId(pluginDescriptor.getGroupId());
      builtinExtract.setVersion(pluginDescriptor.getVersion());
      builtinExtract.setLoadAsNeeded(true);
      extracts.setExtract(builtinExtract);
    }

    ExtractRoot root = new ExtractRoot(context);
    root.setOptionSets(ImmutableList.of(extracts));

    ImmutableSortedSet<FileExt> allExtensions = extensionsFor(extracts);

    joinNodes.pipeline()
        .then(root)
        .then(new ResolveExtracts(context))
        .then(new ExtractFiles(context))
        .provide(allExtensions)
        .build();
  }

  static ImmutableSortedSet<FileExt> extensionsFor(Extracts extracts) {
    ImmutableSortedSet.Builder<FileExt> b = ImmutableSortedSet.naturalOrder();
    for (Extract e : extracts.getExtracts()) {
      for (String suffix : e.getSuffixes()) {
        b.add(FileExt.valueOf(suffix));
      }
    }
    return b.build();
  }

}
