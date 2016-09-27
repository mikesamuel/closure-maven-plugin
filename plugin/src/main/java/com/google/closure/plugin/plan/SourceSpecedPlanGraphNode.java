package com.google.closure.plugin.plan;

import java.io.IOException;

import org.codehaus.plexus.util.Scanner;

import com.google.closure.plugin.common.DirectoryScannerSpec;
import com.google.closure.plugin.common.TypedFile;

/**
 * A plan graph node that detects whether inputs are changed by using the
 * build context to look at the {@link DirectoryScannerSpec} roots.
 */
public abstract
class SourceSpecedPlanGraphNode<V extends PlanGraphNode.StateVector>
extends PlanGraphNode<V> {

  protected SourceSpecedPlanGraphNode(PlanContext context) {
    super(context);
  }

  @Override
  protected boolean hasChangedInputs() throws IOException {
    if (!context.buildContext.isIncremental()) {
      return true;
    }

    DirectoryScannerSpec inputFileSpec = getSourceSpec();
    for (TypedFile root : inputFileSpec.roots) {
      Scanner s = context.buildContext.newScanner(root.f, false);
      s.setExcludes(inputFileSpec.excludes.toArray(EMPTY_STRING_ARRAY));
      s.setIncludes(inputFileSpec.includes.toArray(EMPTY_STRING_ARRAY));
      s.scan();
      if (s.getIncludedFiles() != null) {
        return true;
      }

      s = context.buildContext.newDeleteScanner(root.f);
      s.setExcludes(inputFileSpec.excludes.toArray(EMPTY_STRING_ARRAY));
      s.setIncludes(inputFileSpec.includes.toArray(EMPTY_STRING_ARRAY));
      s.scan();
      if (s.getIncludedFiles() != null) {
        return true;
      }
    }
    return false;
  }

  protected abstract DirectoryScannerSpec getSourceSpec();

  private static final String[] EMPTY_STRING_ARRAY = new String[0];
}
