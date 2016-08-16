package com.google.common.html.plugin.plan;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import org.junit.Test;

import com.google.common.base.Optional;
import com.google.common.html.plugin.TestLog;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class HashStoreTest extends TestCase {

  @Test
  public static final void testReadWrite() throws IOException {
    PlanKey fooKey = PlanKey.builder("foo").build();
    PlanKey barKey = PlanKey.builder("bar").build();
    PlanKey bazKey = PlanKey.builder("baz").build();

    HashStore hs = new HashStore();
    hs.setHash(fooKey, Hash.hashString("foo"));
    hs.setHash(barKey, Hash.hashString("bar"));

    StringWriter sw = new StringWriter();
    hs.write(sw);
    String written = sw.toString();

    StringReader sr = new StringReader(written);
    HashStore read = HashStore.read(sr, new TestLog());
    for (HashStore oneHs : new HashStore[] { hs, read }) {
      assertEquals(Optional.of(Hash.hashString("foo")), oneHs.getHash(fooKey));
      assertEquals(Optional.of(Hash.hashString("bar")), oneHs.getHash(barKey));
      assertFalse(oneHs.getHash(bazKey).isPresent());
    }
  }

}
