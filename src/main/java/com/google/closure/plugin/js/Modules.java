package com.google.closure.plugin.js;

import java.io.Serializable;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.closure.plugin.common.Sources.Source;
import com.google.javascript.jscomp.CommandLineRunner;

/**
 * A list of JS modules with enough dependency information to generate
 * {@link CommandLineRunner} flags.
 */
public final class Modules implements Serializable {
  private static final long serialVersionUID = -6277668858371099203L;

  /** Modules in dependency order. */
  public final ImmutableList<Module> modules;

  /**
   * @param modules depended-upon modules must appear before their dependers.
   */
  Modules(ImmutableList<Module> modules) {
    this.modules = modules;

    Map<String, Integer> moduleToIndex = Maps.newHashMap();
    for (Module m : modules) {
      Integer index = moduleToIndex.size();
      Integer prev = moduleToIndex.put(m.name, index);
      if (prev != null) {
        throw new IllegalArgumentException(
            "Duplicate modules with name " + m.name + " at indices "
            + index + ", " + prev);
      }
    }

    for (Module m : modules) {
      Integer mi = Preconditions.checkNotNull(moduleToIndex.get(m.name));
      for (String dep : m.deps) {
        Integer di = moduleToIndex.get(dep);
        if (di == null) {
          throw new IllegalArgumentException(
              "Unsatisfied dependency " + dep + " required by " + m.name);
        }
        if (di >= mi) {
          throw new IllegalArgumentException(
              m.name + " appears before its dependency " + dep);
        }
      }
    }
  }

  /**
   * Adds flags to declare this module to {@link CommandLineRunner}.
   *
   * @param argv receives flags.
   */
  public void addClosureCompilerFlags(
      ImmutableList.Builder<? super String> argv) {
    for (Module module : modules) {
      StringBuilder moduleSpec = new StringBuilder();
      Preconditions.checkState(!module.name.contains(":"));
      moduleSpec.append(module.name)
          .append(':')
          .append(module.sources.size());
      char sep = ':';
      for (String dep : module.deps) {
        moduleSpec.append(sep);
        sep = ',';
        Preconditions.checkState(!dep.contains(",") && !dep.contains(":"));
        moduleSpec.append(dep);
      }
      argv.add("--module").add(moduleSpec.toString());
      for (Source source : module.sources) {
        argv.add("--js").add(source.canonicalPath.getPath());
      }
    }
  }


  /** A single JS module definition. */
  public static final class Module implements Serializable {
    private static final long serialVersionUID = -8460629612062250977L;

    /** Module name. */
    public final String name;
    /** Modules upon which this module depends. */
    public final ImmutableList<String> deps;
    /** Source files for this module. */
    public final ImmutableList<Source> sources;

    /** @param sources goog.providers must appear before their requirers */
    Module(
        String name, ImmutableList<String> deps,
        ImmutableList<Source> sources) {
      // comma and colon are meta-characters in the --module flag value.
      Preconditions.checkArgument(!name.contains(",") && !name.contains(":"));
      this.name = name;
      this.deps = deps;
      this.sources = sources;
    }

    @Override
    public String toString() {
      return "{"
          + name + ":" + sources.size() + ":" + deps + " " + sources
          + "}";
    }
  }
}
