package com.google.closure.plugin.common;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Finds an executable file that can be used to compile source files.
 */
public interface ToolFinder<OPTIONS> {
  /**
   * @param options options that may override the default search path.
   * @param ingredients used to construct file ingredients.
   * @param toolPathOut receives the tool files as main sources.
   */
  void find(Log log, OPTIONS options, Sink out);


  /** Receives the found tool if not already specified. */
  public final class Sink {
    private Optional<File> f = Optional.absent();
    private MojoExecutionException problem;

    /** Present if found. */
    public synchronized Optional<File> get() throws MojoExecutionException {
      if (problem != null) {
        throw problem;
      }
      return f;
    }

    /** Stores the found file. */
    public void set(File found) {
      Optional<File> newF = Optional.of(found);
      synchronized (this) {
        this.f = newF;
      }
    }

    /**
     * A problem that will raised next time get is called.
     */
    public void setProblem(Throwable th) {
      Preconditions.checkNotNull(th);
      MojoExecutionException mee;
      if (th instanceof MojoExecutionException) {
        mee = (MojoExecutionException) th;
      } else {
        mee = new MojoExecutionException(null, th);
      }
      synchronized (this) {
        problem = mee;
      }
    }
  }
}
