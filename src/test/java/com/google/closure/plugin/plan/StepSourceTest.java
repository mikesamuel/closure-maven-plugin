package com.google.closure.plugin.plan;

import org.junit.Test;

import com.google.common.collect.ImmutableSet;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public class StepSourceTest extends TestCase {

  @Test
  public static final void testAllGenerated() {
    ImmutableSet.Builder<StepSource> allGenerated = ImmutableSet.builder();
    for (StepSource ss : StepSource.values()) {
      if (ss.name().endsWith("_GENERATED")) {
        allGenerated.add(ss);
      }
    }
    assertEquals(allGenerated.build(), StepSource.ALL_GENERATED);
  }

  @Test
  public static final void testAllCompiled() {
    ImmutableSet.Builder<StepSource> allGenerated = ImmutableSet.builder();
    for (StepSource ss : StepSource.values()) {
      if (ss.name().endsWith("_COMPILED")) {
        allGenerated.add(ss);
      }
    }
    assertEquals(allGenerated.build(), StepSource.ALL_COMPILED);
  }

}
