package com.google.closure.plugin.common;

import org.junit.Test;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class TopoSortTest extends TestCase {

  @Test
  public static void testEmpty() throws Exception {
    Function<String, ImmutableList<String>> failHard =
        new Function<String, ImmutableList<String>>() {
          @Override
          public ImmutableList<String> apply(String input) {
            throw new AssertionError(input);
          }
        };

    TopoSort<String, String> ts = new TopoSort<>(
        failHard, failHard,
        ImmutableList.<String>of());

    assertEquals(ImmutableList.of(), ts.getSortedItems());
  }

  @Test
  public static void testSort() throws Exception {
    // For the counting numbers [0, 1000],
    // each number depends on x / 2 and x / 4, and provides its integer factors.

    ImmutableList.Builder<Integer> zeroToOneThousandInclusive =
        ImmutableList.builder();
    for (int i = 0; i <= 1000; ++i) {
      zeroToOneThousandInclusive.add(i);
    }

    TopoSort<Integer, String> ts = new TopoSort<>(
        new Function<Integer, ImmutableList<String>>() {
          @Override
          public ImmutableList<String> apply(Integer i) {
            return ImmutableList.of("" + (i / 4), "" + (i / 2));
          }
        },
        new Function<Integer, ImmutableList<String>>() {
          @Override
          public ImmutableList<String> apply(Integer i) {
            ImmutableList.Builder<String> provides = ImmutableList.builder();
            if (i > 0) {
              for (int k = i; k <= 1000; k += i) {
                provides.add("" + k);
              }
            } else {
              provides.add("0");
            }
            return provides.build();
          }
        },
        zeroToOneThousandInclusive.build());

    // Since each number requires only smaller numbers, and the sort is mostly
    // stable, the sorted output is [0..1000].
    assertEquals(
        zeroToOneThousandInclusive.build(),
        ts.getSortedItems());
  }
}
