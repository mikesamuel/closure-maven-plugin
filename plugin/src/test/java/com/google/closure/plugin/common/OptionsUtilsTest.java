package com.google.closure.plugin.common;

import java.util.Arrays;

import org.junit.Test;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.closure.plugin.common.Options;
import com.google.closure.plugin.common.OptionsUtils;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class OptionsUtilsTest extends TestCase {

  public static final class TestOptions extends Options {

    private static final long serialVersionUID = 1664053510188753781L;

    @Asplodable
    public E[] e;

    public String s;

    @Asplodable
    public F[] f;

    public G[] g;

    TestOptions() {
      // null id
    }

    TestOptions(String id) {
      this.id = id;
    }

    @Override
    protected void createLazyDefaults() {
      s = "default";
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
      keys.add(o.getKey().text);
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

    assertTrue(opts.get(0).wasIdImplied);
    assertFalse(opts.get(3).wasIdImplied);
  }


  @Test
  public static void testPrepare() throws Exception {
    TestOptions t0 = new TestOptions();
    t0.e = new E[] { E.X, E.Y };
    t0.f = new F[] { F.A, F.B };

    TestOptions t1 = new TestOptions();
    t1.e = new E[] { E.Y, E.Z };
    t1.g = new G[] { G.M, G.N };

    TestOptions t2 = new TestOptions();
    t2.g = new G[] { G.M, G.N };

    ImmutableList<TestOptions> prepared = OptionsUtils.prepare(
        new Supplier<TestOptions>() {
          @Override
          public TestOptions get() {
            return new TestOptions();
          }
        },
        ImmutableList.of(t0, t1, t2));

    StringBuilder sb = new StringBuilder();
    for (TestOptions t : prepared) {
      sb.append(t.getId());
      if (t.e != null) {
        sb.append(" e=").append(Arrays.toString(t.e));
      }
      if (t.f != null) {
        sb.append(" f=").append(Arrays.toString(t.f));
      }
      if (t.g != null) {
        sb.append(" g=").append(Arrays.toString(t.g));
      }
      sb.append('\n');
    }

    assertEquals(
        ""
        + "test.0 e=[X] f=[A]\n"
        + "test.1 e=[X] f=[B]\n"
        + "test.2 e=[Y] f=[A]\n"
        + "test.3 e=[Y] f=[B]\n"
        + "test.4 e=[Y] g=[M, N]\n"
        + "test.5 e=[Z] g=[M, N]\n"
        + "test.6 g=[M, N]\n",
        sb.toString());
  }


  enum E {
    X,
    Y,
    Z,
    ;
  }


  enum F {
    A,
    B,
    C,
    ;
  }


  enum G {
    M,
    N,
    ;
  }
}
