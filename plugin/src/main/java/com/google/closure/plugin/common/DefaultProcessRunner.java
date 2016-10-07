package com.google.closure.plugin.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Charsets;
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
      final Log log, final String logPrefix, Iterable<? extends String> argv,
      final OutputReceiver receiver) {
    final ImmutableList<String> argumentList = ImmutableList.copyOf(argv);

    // Holder for a bit that tells whether execution was cancelled.
    final boolean[] cancelled = new boolean[1];
    // Holder for the exit code.  Null if not done.
    final Integer[] exitCode = new Integer[1];
    // The process once it has started.
    final Process[] process = new Process[1];
    final long[] startTime = new long[] { System.nanoTime() };

    Runnable runner = new Runnable() {
      @SuppressWarnings("synthetic-access")
      @Override
      public void run() {
        ProcessBuilder processBuilder = new ProcessBuilder(argumentList);
        if (log.isDebugEnabled()) {
          log.debug("Running " + logPrefix + ": " + argumentList);
        }
        try {
          Process p = null;
          synchronized (exitCode) {
            startTime[0] = System.nanoTime();
            if (cancelled[0]) {
              exitCode[0] = CANCELLED_EXIT_STATUS;
            } else {
              p = process[0] = processBuilder.start();
            }
            exitCode.notifyAll();
          }
          if (p != null) {
            p.getOutputStream().close();
            AtomicInteger watcherCounter = new AtomicInteger();
            attachProcessOutputSink(
                log, logPrefix, p.getInputStream(), receiver,
                watcherCounter);
            attachProcessOutputSink(
                log, logPrefix, p.getErrorStream(), receiver,
                watcherCounter);
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
          Integer ec;
          synchronized (exitCode) {
            p = process[0];
            c = cancelled[0];
            t0 = startTime[0];
            ec = exitCode[0];
            if (p == null && !c) {
              try {
                exitCode.wait();
              } catch (InterruptedException ex) {
                log.info("Watcher for " + logPrefix + " interrupted", ex);
              }
            }
          }
          if (ec != null || c) {
            break;
          } else if (p != null) {
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

  private static final Executor executor = Executors.newFixedThreadPool(8);

  private static void attachProcessOutputSink(
      final Log log, final String prefix, final InputStream processOutput,
      final OutputReceiver receiver, final AtomicInteger watcherCounter) {
    watcherCounter.incrementAndGet();
    executor.execute(new Runnable() {

      @Override
      public void run() {
        try (InputStream in = processOutput) {
          try (BufferedReader reader = new BufferedReader(
                 new InputStreamReader(in, Charsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
              receiver.processLine(line);
            }
          }
        } catch (IOException ex) {
          log.error("Failed to read process output for " + prefix, ex);
        } finally {
          if (watcherCounter.decrementAndGet() == 0) {
            receiver.allProcessed();
          }
        }
      }

    });
  }
}
