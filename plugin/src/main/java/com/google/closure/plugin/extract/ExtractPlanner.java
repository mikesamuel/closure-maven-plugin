package com.google.closure.plugin.extract;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;

import com.google.closure.plugin.common.FileExt;
import com.google.closure.plugin.common.OptionsUtils;
import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;
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
  public PlanGraphNode<?> plan(Extracts unpreparedExtracts)
  throws MojoExecutionException {

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

    ResolveExtracts re = new ResolveExtracts(context, extracts);
    joinNodes.pipeline(
        // Dependencies are outside the project.
        ImmutableSortedSet.<FileExt>of(),
        re,
        ExtractFiles.extensionsFor(extracts));
    return re;
  }
}
