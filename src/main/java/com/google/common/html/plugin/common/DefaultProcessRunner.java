package com.google.common.html.plugin.common;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.maven.plugin.logging.Log;

import com.google.common.collect.ImmutableList;

/**
 * A process runner backed by {@link ProcessBuilder}.
 * Most of this is just back-porting Java 8's
 * {@code Process.waitFor(duration, unit)} badly.
 */
public final class DefaultProcessRunner implements ProcessRunner{
  private DefaultProcessRunner() {
    // singleton
  }

  /** Pseudo exit code that indicates execution was cancelled. */
  public static final int CANCELLED_EXIT_STATUS = Integer.MIN_VALUE;
  /** Pseudo exit code that indicates process waiting was interrupted. */
  public static final int INTERRUPTED_EXIT_STATUS = Integer.MIN_VALUE + 1;

  /** Singleton */
  public static final DefaultProcessRunner INSTANCE =
      new DefaultProcessRunner();

  @Override
  public Future<Integer> run(
      final Log log, final String logPrefix, Iterable<? extends String> argv) {
    final ImmutableList<String> argumentList = ImmutableList.copyOf(argv);

    // Holder for a bit that tells whether execution was cancelled.
    final boolean[] cancelled = new boolean[1];
    // Holder for the exit code.  Null if not done.
    final Integer[] exitCode = new Integer[1];
    // The process once it has started.
    final Process[] process = new Process[1];
    final long[] startTime = new long[] { System.nanoTime() };

    Runnable runner = new Runnable() {
      @Override
      public void run() {
        ProcessBuilder protocProcessBuilder = new ProcessBuilder(argumentList)
            .inheritIO();
        if (log.isDebugEnabled()) {
          log.debug("Running " + logPrefix + ": " + argumentList);
        }
        try {
          synchronized (exitCode) {
            startTime[0] = System.nanoTime();
            if (cancelled[0]) {
              exitCode[0] = CANCELLED_EXIT_STATUS;
            } else {
              process[0] = protocProcessBuilder.start();
            }
            exitCode.notifyAll();
          }
        } catch (IOException ex) {
          log.error("Process " + logPrefix + " did not start normally", ex);
          synchronized (exitCode) {
            exitCode[0] = -1;
          }
        }
      }
    };

    Runnable watcher = new Runnable() {

      @Override
      public void run() {
        while (true) {
          Process p;
          boolean c;
          long t0;
          synchronized (exitCode) {
            p = process[0];
            c = cancelled[0];
            t0 = startTime[0];
            if (p == null && !c) {
              try {
                exitCode.wait();
              } catch (InterruptedException ex) {
                log.info("Watcher for " + logPrefix + " interrupted", ex);
              }
            }
          }
          if (c) {
            break;
          } else if (p != null) {
            int ec;
            try {
              ec = process[0].waitFor();
            } catch (InterruptedException ex) {
              log.info("Watcher for " + logPrefix + " interrupted", ex);
              ec = INTERRUPTED_EXIT_STATUS;
            }
            long t1 = System.nanoTime();
            log.info(
                "Execution of " + logPrefix + " completed with exit code " + ec
                + " after " + ((t1 - t0) / (1000000L /* ns / ms */)) + "ms");
            synchronized (exitCode) {
              exitCode[0] = ec;
              exitCode.notifyAll();
            }
          }
        }
      }
    };

    Thread runnerThread = new Thread(runner);
    runnerThread.setDaemon(true);
    runnerThread.setName(logPrefix + " runner");
    runnerThread.start();

    Thread watcherThread = new Thread(watcher);
    watcherThread.setDaemon(true);
    watcherThread.setName(logPrefix + " watcher");
    watcherThread.start();

    return new Future<Integer>() {

      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        boolean didCancel;
        synchronized (exitCode) {
          didCancel = !cancelled[0];
          cancelled[0] = true;
          if (mayInterruptIfRunning && process[0] != null) {
            process[0].destroy();
          }
          exitCode.notifyAll();
        }
        return didCancel;
      }

      @Override
      public boolean isCancelled() {
        synchronized (exitCode) {
          return cancelled[0];
        }
      }

      @Override
      public boolean isDone() {
        synchronized (exitCode) {
          return exitCode[0] != null;
        }
      }

      @Override
      public Integer get() throws InterruptedException, ExecutionException {
        while (true) {
          synchronized (exitCode) {
            if (exitCode[0] != null) {
              return exitCode[0];
            }
            exitCode.wait();
          }
        }
      }

      @Override
      public Integer get(long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException {
        while (true) {
          synchronized (exitCode) {
            if (exitCode[0] != null) {
              return exitCode[0];
            }
            exitCode.wait(unit.toMillis(timeout));
          }
        }
      }
    };
  }

}
