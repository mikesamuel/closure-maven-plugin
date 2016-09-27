package com.google.closure.plugin.common;

import java.util.List;

import org.junit.Test;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.closure.plugin.common.Options;
import com.google.closure.plugin.common.OptionsUtils;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class OptionsUtilsTest extends TestCase {

  public static final class TestOptions extends Options {

    private static final long serialVersionUID = 1664053510188753781L;

    @Asplodable
    private final List<E> e = Lists.newArrayList();

    public void setE(E x) {
      e.add(x);
    }

    public String s;

    @Asplodable
    private final List<F> f = Lists.newArrayList();

    public void setF(F x) {
      f.add(x);
    }

    private final List<G> g = Lists.newArrayList();

    public void setG(G x) {
      g.add(x);
    }

    public TestOptions() {
      // null id
    }

    public TestOptions(String id) {
      this.id = id;
    }

    @Override
    protected void createLazyDefaults() {
      s = "default";
    }

    public ImmutableList<E> getE() {
      return ImmutableList.copyOf(e);
    }

    public ImmutableList<F> getF() {
      return ImmutableList.copyOf(f);
    }

    public ImmutableList<G> getG() {
      return ImmutableList.copyOf(g);
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
    for (TestOptions o : opts) {
      ids.add(o.getId());
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

    assertTrue(opts.get(0).wasIdImplied);
    assertFalse(opts.get(3).wasIdImplied);
  }


  @Test
  public static void testPrepare() throws Exception {
    TestOptions t0 = new TestOptions();
    t0.setE(E.X);
    t0.setE(E.Y);
    t0.setF(F.A);
    t0.setF(F.B);

    TestOptions t1 = new TestOptions();
    t1.setE(E.Y);
    t1.setE(E.Z);
    t1.setG(G.M);
    t1.setG(G.N);

    TestOptions t2 = new TestOptions();
    t2.setG(G.M);
    t2.setG(G.N);

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
      if (!t.getE().isEmpty()) {
        sb.append(" e=").append(t.getE());
      }
      if (!t.getF().isEmpty()) {
        sb.append(" f=").append(t.getF());
      }
      if (!t.getG().isEmpty()) {
        sb.append(" g=").append(t.getG());
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
