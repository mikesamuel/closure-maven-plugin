package com.google.closure.plugin.common;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.closure.plugin.TestLog;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class DefaultProcessRunnerTest extends TestCase {

  @Test
  public static void testEcho() throws Exception {
    TestOutputReceiver receiver = new TestOutputReceiver();
    Future<Integer> exitCode = DefaultProcessRunner.INSTANCE.run(
        new TestLog(), "echo",
        // Not Windows compatible.
        // This is flaky, but it's better to get some coverage on standard
        // configurations than to not test because Windows' has legacy file
        // system issues.
        ImmutableList.of("/bin/echo", "Hello", "World"),
        receiver);
    assertEquals(0, exitCode.get(10, TimeUnit.SECONDS).intValue());
    receiver.waitUntilDone();
    assertEquals("Hello World", receiver.sb.toString());
  }


  static final class TestOutputReceiver
  implements ProcessRunner.OutputReceiver {
    final StringBuffer sb = new StringBuffer();
    private boolean done;

    @Override
    public void processLine(String line) {
      sb.append(line);
    }

    @Override
    public void allProcessed() {
      synchronized (this) {
        done = true;
        this.notifyAll();
      }
    }

    void waitUntilDone() throws InterruptedException {
      while (true) {
        synchronized (this) {
          if (done) { return; }
          this.wait();
        }
      }
    }
  }
}
