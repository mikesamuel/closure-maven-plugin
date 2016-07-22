package com.google.common.html.plugin;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class OptionsUtilsTest extends TestCase {

  public static final class TestOptions extends Options {

    private static final long serialVersionUID = 1664053510188753781L;

    TestOptions() {
      // null id
    }

    TestOptions(String id) {
      this.id = id;
    }
  }

  @Test
  public static void testDisambiguateIds() {
    ImmutableList<TestOptions> opts = ImmutableList.<TestOptions>of(
        new TestOptions(),
        new TestOptions("i"),
        new TestOptions("i"),
        new TestOptions("j"),
        new TestOptions(),
        new TestOptions("i"),
        new TestOptions("i.1"),
        new TestOptions(),
        new TestOptions(""));

    OptionsUtils.disambiguateIds(opts);

    ImmutableList.Builder<String> ids = ImmutableList.builder();
    ImmutableSet.Builder<String> keys = ImmutableSet.builder();
    for (TestOptions o : opts) {
      ids.add(o.getId());
      keys.add(o.getKey());
    }

    assertEquals(
        ImmutableList.of(
            "test.0",
            "i.0",
            "i.2",
            "j",
            "test.1",
            "i.3",
            "i.1",
            "test.2",
            "test.3"),
        ids.build());
    assertEquals(
        opts.size(),
        keys.build().size());

  }

}
