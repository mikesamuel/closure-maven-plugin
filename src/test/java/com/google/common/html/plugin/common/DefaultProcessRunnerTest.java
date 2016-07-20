package com.google.common.html.plugin.common;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.html.plugin.TestLog;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class DefaultProcessRunnerTest extends TestCase {

  @Test
  public static void testEcho() throws Exception {
    Future<Integer> exitCode = DefaultProcessRunner.INSTANCE.run(
        new TestLog(), "echo",
        // Not Windows compatible.
        // This is flaky, but it's better to get some coverage on standard
        // configurations than to not test because Windows' has legacy file
        // system issues.
        ImmutableList.of("/bin/echo", "Hello", "World"));
    assertEquals(0, exitCode.get(10, TimeUnit.SECONDS).intValue());
  }

}
