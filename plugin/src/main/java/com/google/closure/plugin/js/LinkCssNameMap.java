package com.google.closure.plugin.js;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.closure.plugin.plan.JoinNodes;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraphNode;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.css.OutputRenamingMapFormat;

/**
 * Outputs the CSS Name map as a JavaScript source.
 */
final class LinkCssNameMap extends PlanGraphNode<LinkCssNameMap.SV> {
  LinkCssNameMap(PlanContext context) {
    super(context);
  }

  File getJsRenameMap() {
    return new File(context.genfilesDirs.jsGenfiles, "css-rename-map.js");
   }

  @Override
  protected void processInputs() throws IOException, MojoExecutionException {
    File jsRenameMap = getJsRenameMap();

    jsRenameMap.getParentFile().mkdirs();
    try (OutputStream out = new FileOutputStream(jsRenameMap)) {
      try (Writer writer = new OutputStreamWriter(out, Charsets.UTF_8)) {
        writer.write("// Autogenerated by ");
        writer.write(getClass().getName());
        writer.write("\n");

        OutputRenamingMapFormat.CLOSURE_COMPILED_BY_WHOLE.writeRenamingMap(
            context.substitutionMapProvider.get().getMappings(),
            writer);
        // TODO: freeze the renaming map so no new entries can be added.
      }
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to link CSS rename map to JS", ex);
    }
  }
  @Override
  protected boolean hasChangedInputs() throws IOException {
    return context.substitutionMapProvider.hasChanged();
  }

  @Override
  protected
  Optional<ImmutableList<PlanGraphNode<?>>> rebuildFollowersList(JoinNodes jn)
  throws MojoExecutionException {
    return Optional.absent();
  }

  @Override
  protected void markOutputs() {
    context.buildContext.refresh(getJsRenameMap());
  }

  @Override
  protected SV getStateVector() {
    return new SV();
  }


  static final class SV implements PlanGraphNode.StateVector {

    private static final long serialVersionUID = 1L;

    @Override
    public PlanGraphNode<?> reconstitute(PlanContext c, JoinNodes jn) {
      return new LinkCssNameMap(c);
    }
  }
}
