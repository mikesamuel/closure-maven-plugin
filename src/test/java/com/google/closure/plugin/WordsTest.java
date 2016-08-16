package com.google.closure.plugin;

import org.junit.Test;

import com.google.closure.plugin.common.Words;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class WordsTest extends TestCase {

  @Test
  public static final void testEndsWithWordOrIs() {
    assertTrue(Words.endsWithWordOrIs("foo", "foo"));
    assertTrue(Words.endsWithWordOrIs("FOO", "foo"));
    assertTrue(Words.endsWithWordOrIs("bar-foo", "foo"));
    assertFalse(Words.endsWithWordOrIs("foo-bar", "foo"));
    assertFalse(Words.endsWithWordOrIs("barfoo", "foo"));
    assertFalse(Words.endsWithWordOrIs("bar-food", "foo"));
  }

}
