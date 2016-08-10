package com.google.common.html.plugin.js;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.common.html.plugin.common.TopoSort;
import com.google.common.html.plugin.common.CommonPlanner;
import com.google.common.html.plugin.common.Ingredients.FileIngredient;
import com.google.common.html.plugin.common.Ingredients.FileSetIngredient;
import com.google.common.html.plugin.common.Ingredients.OptionsIngredient;
import com.google.common.html.plugin.common.Ingredients.PathValue;
import com.google.common.html.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.common.html.plugin.common.Sources.Source;
import com.google.common.html.plugin.common.SourceFileProperty;
import com.google.common.html.plugin.plan.Ingredient;
import com.google.common.html.plugin.plan.PlanKey;
import com.google.common.html.plugin.plan.Step;
import com.google.common.html.plugin.plan.StepSource;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.SourceFile;

final class ComputeJsDepGraph extends Step {
  final JsPlanner planner;
  private SerializedObjectIngredient<Modules> modulesIngForPlanner;

  public ComputeJsDepGraph(
      JsPlanner planner,
      OptionsIngredient<JsOptions> optionsIng,
      FileSetIngredient sources) {
    super(
        PlanKey.builder("js-module-graph")
            .addInp(optionsIng, sources)
            .build(),
        ImmutableList.<Ingredient>of(optionsIng, sources),
        Sets.immutableEnumSet(StepSource.JS_GENERATED, StepSource.JS_SRC),
        Sets.immutableEnumSet(StepSource.JS_COMPILED));
    this.planner = planner;
  }

  synchronized SerializedObjectIngredient<Modules> getModulesIng()
  throws IOException {
    if (modulesIngForPlanner == null) {
      modulesIngForPlanner = planner.planner.ingredients.serializedObject(
        new File(new File(planner.planner.outputDir, "js"), "modules.ser"),
        Modules.class);
    }
    return modulesIngForPlanner;
  }


  @Override
  public void execute(Log log) throws MojoExecutionException {
    OptionsIngredient<JsOptions> optionsIng =
        ((OptionsIngredient<?>) inputs.get(0)).asSuperType(JsOptions.class);
    FileSetIngredient sources = (FileSetIngredient) inputs.get(1);

    Modules modules = execute(
        log,
        optionsIng.getOptions(),
        new CompilerInputFactory() {
          @Override
          public CompilerInput create(Source s) {
            SourceFile sourceFile = new SourceFile.Builder()
                .withCharset(Charsets.UTF_8)
                .withOriginalPath(s.relativePath.getPath())
                .buildFromFile(s.canonicalPath);
            return new CompilerInput(sourceFile);
          }
        },
        Lists.transform(sources.sources(), FileIngredient.GET_SOURCE));

    try {
      SerializedObjectIngredient<Modules> modulesIng = getModulesIng();
      modulesIng.setStoredObject(modules);
      modulesIng.write();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to store JS module graph", ex);
    }
  }

  @VisibleForTesting
  static Modules execute(
      Log log,
      JsOptions options,
      CompilerInputFactory ciFactory,
      Iterable<? extends Source> sources)
  throws MojoExecutionException {

    Multimap<ModuleName, CompilerInputAndSource> compilerInputsPerModule =
        Multimaps.newMultimap(
            Maps.<ModuleName, Collection<CompilerInputAndSource>>newTreeMap(),
            new Supplier<Collection<CompilerInputAndSource>>() {
              @Override
              public Collection<CompilerInputAndSource> get() {
                return Sets.newTreeSet(
                    new Comparator<CompilerInputAndSource>() {
                      @Override
                      public int compare(
                          CompilerInputAndSource a, CompilerInputAndSource b) {
                        return a.s.relativePath.compareTo(b.s.relativePath);
                      }
                    });
              }
            });

    Predicate<Source> isTestOnly = new Predicate<Source>() {
      @Override
      public boolean apply(Source s) {
        return s.root.ps.contains(SourceFileProperty.TEST_ONLY);
      }
    };

    Iterable<? extends Source> mainSources = Iterables.filter(
        sources, Predicates.not(isTestOnly));
    Iterable<? extends Source> testSources = Iterables.filter(
        sources, isTestOnly);

    collectSourceFiles(
        "main", mainSources, compilerInputsPerModule, ciFactory);
    collectSourceFiles(
        "test", testSources, compilerInputsPerModule, ciFactory);

    final Compiler parsingCompiler = new Compiler(
        new MavenLogJSErrorManager(log));
    parsingCompiler.initOptions(options.toCompilerOptions());

    // goog.provide and goog.require are ambiently available.
    final ImmutableSet<GoogNamespace> ambientlyAvailable =
        ImmutableSortedSet.of(new GoogNamespace("goog"));

    TopoSort<ModuleName, GoogNamespace> moduleTopoSort;
    {
      Map<ModuleName, Set<GoogNamespace>> allRequires = Maps.newLinkedHashMap();
      Map<ModuleName, Set<GoogNamespace>> allProvides = Maps.newLinkedHashMap();
      for (Map.Entry<ModuleName, Collection<CompilerInputAndSource>> e
          : compilerInputsPerModule.asMap().entrySet()) {
       ModuleName moduleName = e.getKey();

       Set<GoogNamespace> reqs = Sets.newTreeSet();
       ImmutableSet.Builder<GoogNamespace> provsBuilder = ImmutableSet.builder();
       for (CompilerInputAndSource cis : e.getValue()) {
         cis.ci.setCompiler(parsingCompiler);
         reqs.addAll(GoogNamespace.allOf(cis.ci.getRequires()));
         provsBuilder.addAll(GoogNamespace.allOf(cis.ci.getProvides()));
       }
       // We don't need to require internally provided symbols.
       ImmutableSet<GoogNamespace> provs = provsBuilder.build();
       reqs.removeAll(provs);

       allRequires.put(moduleName, ImmutableSet.copyOf(reqs));
       allProvides.put(moduleName, provs);
      }
      try {
        moduleTopoSort = new TopoSort<>(
            Functions.forMap(allRequires),
            Functions.forMap(allProvides),
            compilerInputsPerModule.keySet(),
            ambientlyAvailable);
      } catch (TopoSort.CyclicRequirementException ex) {
        throw new MojoExecutionException("Mismatched require/provides", ex);
      } catch (TopoSort.MissingRequirementException ex) {
        throw new MojoExecutionException("Mismatched require/provides", ex);
      }
    }
    ImmutableList<ModuleName> moduleOrder = moduleTopoSort.getSortedItems();

    // When computing the internal ordering, we don't need to require things
    // that are provided by dependencies and which are not provided internally.
    // Since we've already figured out the dependency order, we just keep a
    // running total of symbols to subtract from the current module's required
    // set.
    final Set<GoogNamespace> providedByPossibleDependencies = Sets.newTreeSet();

    ImmutableList.Builder<Modules.Module> moduleList = ImmutableList.builder();
    for (ModuleName moduleName : moduleOrder) {
      final Map<String, CompilerInputAndSource> inputsByCiName =
          Maps.newLinkedHashMap();
      Collection<CompilerInputAndSource> moduleSources =
          compilerInputsPerModule.get(moduleName);
      for (CompilerInputAndSource cis : moduleSources) {
        String ciName = cis.ci.getName();
        Preconditions.checkState(
            null == inputsByCiName.put(ciName, cis));
      }

      TopoSort<CompilerInputAndSource, GoogNamespace> compilerInputsTopoSort;
      try {
        compilerInputsTopoSort = new TopoSort<>(
            new Function<CompilerInputAndSource, Collection<GoogNamespace>>() {
              @Override
              public Collection<GoogNamespace>
              apply(CompilerInputAndSource cis) {
                ImmutableList<GoogNamespace> reqs =
                    GoogNamespace.allOf(cis.ci.getRequires());
                ImmutableList.Builder<GoogNamespace> reqsFiltered =
                    ImmutableList.builder();
                for (GoogNamespace req : reqs) {
                  if (!providedByPossibleDependencies.contains(req)) {
                    reqsFiltered.add(req);
                  }
                }
                return reqsFiltered.build();
              }
            },
            new Function<CompilerInputAndSource, Collection<GoogNamespace>>() {
              @Override
              public Collection<GoogNamespace>
              apply(CompilerInputAndSource cis) {
                return GoogNamespace.allOf(cis.ci.getProvides());
              }
            },
            inputsByCiName.values(),
            ambientlyAvailable);
      } catch (TopoSort.CyclicRequirementException ex) {
        throw new MojoExecutionException(
            "Mismatched require/provides in module " + moduleName, ex);
      } catch (TopoSort.MissingRequirementException ex) {
        throw new MojoExecutionException(
            "Mismatched require/provides in module " + moduleName, ex);
      }
      ImmutableList<ModuleName> orderedDeps =
          moduleTopoSort.getDependenciesTransitive(moduleName);

      ImmutableList.Builder<Source> orderedSources = ImmutableList.builder();
      for (CompilerInputAndSource cis
           : compilerInputsTopoSort.getSortedItems()) {
        orderedSources.add(cis.s);
        providedByPossibleDependencies.addAll(
            GoogNamespace.allOf(cis.ci.getProvides()));
      }

      // Build a Module with the inputs in topo-order.
      Modules.Module module = new Modules.Module(
          moduleName.text,
          ImmutableList.copyOf(
              Lists.transform(orderedDeps, Identifier.GET_TEXT)),
          orderedSources.build());
      moduleList.add(module);
    }

    return new Modules(moduleList.build());
  }

  @Override
  public void skip(Log log) throws MojoExecutionException {
    try {
      getModulesIng().read();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to load JS module graph", ex);
    }
  }

  @Override
  public ImmutableList<Step> extraSteps(Log log) throws MojoExecutionException {
    OptionsIngredient<JsOptions> optionsIng =
        ((OptionsIngredient<?>) inputs.get(0)).asSuperType(JsOptions.class);

    CommonPlanner commonPlanner = planner.planner;

    PathValue jsOutputDir = commonPlanner.ingredients.pathValue(
        new File(commonPlanner.projectBuildOutputDirectory.value, "js"));

    try {
      return ImmutableList.<Step>of(
          new CompileJs(
              optionsIng, getModulesIng(),
              jsOutputDir));
    } catch (IOException ex) {
      throw new MojoExecutionException("Could not find JS module graph", ex);
    }
  }



  private static void collectSourceFiles(
      String moduleNamePrefix,
      Iterable<? extends Source> sources,
      Multimap<ModuleName, CompilerInputAndSource> sourceFilesByModuleName,
      CompilerInputFactory ciFactory) {
    for (Source s : sources) {
      File parentRelPath = s.relativePath.getParentFile();
      ModuleName moduleName;
      if (parentRelPath != null) {
        moduleName = new ModuleName(
            moduleNamePrefix + "."
            + parentRelPath.getPath().replaceAll("[/\\\\]", "."));
      } else {
        moduleName = new ModuleName(moduleNamePrefix);
      }

      CompilerInput ci = ciFactory.create(s);

      sourceFilesByModuleName.put(
          moduleName,
          new CompilerInputAndSource(ci, s));
    }
  }

  static final class CompilerInputAndSource
  implements Comparable<CompilerInputAndSource> {
    final CompilerInput ci;
    final Source s;

    CompilerInputAndSource(CompilerInput ci, Source s) {
      this.ci = ci;
      this.s = s;
    }

    @Override
    public int compareTo(CompilerInputAndSource that) {
      return this.s.canonicalPath.compareTo(that.s.canonicalPath);
    }

    @Override
    public String toString() {
      return "{Source " + s.canonicalPath + "}";
    }
  }

  interface CompilerInputFactory {
    CompilerInput create(Source source);
  }

  static abstract class Identifier
  implements Comparable<Identifier>, Serializable {
    private static final long serialVersionUID = -5072636170709799520L;

    final String text;

    static final Function<Identifier, String> GET_TEXT =
        new Function<Identifier, String>() {
          @Override
          public String apply(Identifier id) {
            return id.text;
          }
        };

    Identifier(String text) {
      this.text = Preconditions.checkNotNull(text);
    }

    @Override
    public final boolean equals(Object o) {
      if (o == null || o.getClass() != getClass()) {
        return false;
      }
      return text.equals(((Identifier) o).text);
    }

    @Override
    public final int hashCode() {
      return text.hashCode();
    }

    @Override
    public final int compareTo(Identifier that) {
      return this.text.compareTo(that.text);
    }


    @Override
    public String toString() {
      return "{" + getClass().getSimpleName() + " " + text + "}";
    }
  }

  static final class ModuleName extends Identifier {
    private static final long serialVersionUID = 5721482936852117897L;

    ModuleName(String text) {
      super(text);
    }
  }

  static final class GoogNamespace extends Identifier {
    private static final long serialVersionUID = -4018457478547773405L;

    GoogNamespace(String text) {
      super(text);
    }

    static ImmutableList<GoogNamespace> allOf(Iterable<? extends String> xs) {
      ImmutableList.Builder<GoogNamespace> b = ImmutableList.builder();
      for (String x : xs) {
        b.add(new GoogNamespace(x));
      }
      return b.build();
    }
  }
}
