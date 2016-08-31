package com.google.closure.plugin.css;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import com.google.common.css.SourceCode;
import com.google.common.css.SourceCodeLocation;
import com.google.common.css.compiler.ast.CssAtRuleNode;
import com.google.common.css.compiler.ast.CssNode;
import com.google.common.css.compiler.ast.CssNodesListNode;
import com.google.common.css.compiler.ast.CssProvideNode;
import com.google.common.css.compiler.ast.CssRequireNode;
import com.google.common.css.compiler.ast.CssRootNode;
import com.google.common.css.compiler.ast.CssStringNode;
import com.google.common.css.compiler.ast.CssTree;
import com.google.common.css.compiler.ast.CssUnknownAtRuleNode;
import com.google.common.css.compiler.ast.CssValueNode;
import com.google.common.css.compiler.ast.GssParser;
import com.google.common.css.compiler.ast.GssParserException;
import com.google.closure.plugin.common.Words;
import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.common.TopoSort;
import com.google.common.io.Files;

class CssDepGraph {
  /** Maps source relative paths to CssCompiler inputs. */
  final ImmutableMap<Source, SourceCode> inputs;
  /** Maps source relative paths to ASTs. */
  final ImmutableMap<Source, CssTree> parsed;
  /** Maps source relative paths to their dependencies. */
  final ImmutableTable<Source, DepType, ImmutableList<Dep>> deps;
  /** Relative paths of entry style files. */
  final ImmutableList<Source> entryPoints;
  /** Dependencies based on GSS {@code @provide}/{@code @require}. */
  final TopoSort<Source, String> topoSort;

  private static Optional<String> getSoleStringParam(
      CssUnknownAtRuleNode node) {
    List<CssValueNode> parameters = node.getParameters();
    if (parameters != null && parameters.size() == 1) {
      CssValueNode soleValue = parameters.get(0);
      if (soleValue instanceof CssStringNode) {
        return Optional.of(soleValue.getValue());
      }
    }
    return Optional.absent();
  }

  static Optional<Dep> depForNode(CssNode node) {
    if (node instanceof CssProvideNode) {
      return Optional.<Dep>of(new Dep(
          DepType.PROVIDE, ((CssProvideNode) node).getProvide(),
          node.getSourceCodeLocation()));
    } else if (node instanceof CssRequireNode) {
      return Optional.<Dep>of(new Dep(
          DepType.REQUIRE, ((CssRequireNode) node).getRequire(),
          node.getSourceCodeLocation()));
    } else if (node instanceof CssUnknownAtRuleNode) {
      CssUnknownAtRuleNode atRule = (CssUnknownAtRuleNode) node;
      if (CssAtRuleNode.Type.PROVIDE.getCanonicalName().equals(
              atRule.getName().getValue())) {
        Optional<String> symbol = getSoleStringParam(atRule);
        if (symbol.isPresent()) {
          return Optional.<Dep>of(new Dep(
              DepType.PROVIDE, symbol.get(), node.getSourceCodeLocation()));
        }
      } else if (CssAtRuleNode.Type.REQUIRE.getCanonicalName().equals(
              atRule.getName().getValue())) {
        Optional<String> symbol = getSoleStringParam(atRule);
        if (symbol.isPresent()) {
          return Optional.<Dep>of(new Dep(
              DepType.REQUIRE, symbol.get(), node.getSourceCodeLocation()));
        }
      }
    }

    return Optional.absent();
  }

  static void forEachDepRule(
      CssNode node, Function<? super Dep, ?> f) {
    Optional<Dep> importFound = depForNode(node);
    if (importFound.isPresent()) {
      f.apply(importFound.get());
    }
    if (node instanceof CssNodesListNode<?>) {
      for (CssNode child : ((CssNodesListNode<?>) node).getChildren()) {
        forEachDepRule(child, f);
      }
    } else if (node instanceof CssRootNode) {
      forEachDepRule(((CssRootNode) node).getBody(), f);
    }
  }

  CssDepGraph(final Log log, Iterable<? extends Source> sources)
      throws IOException, MojoExecutionException {
    ImmutableMap.Builder<Source, SourceCode> inputsBuilder =
        ImmutableMap.builder();
    ImmutableList.Builder<Source> entryPointsBuilder =
        ImmutableList.builder();
    for (Source source : sources) {
      String fileContent;
      try {
        fileContent = Preconditions.checkNotNull(
            loadContent(source), source.canonicalPath);
      } catch (IOException ex) {
        log.error("Failed to read " + source.canonicalPath);
        throw ex;
      }
      SourceCode sc = new SourceCode(
          source.relativePath.getPath(), fileContent);
      inputsBuilder.put(source, sc);

      String suffixLessName = source.relativePath.getName()
          .replaceFirst("[.](?:css|gss)\\z", "");
      if (Words.endsWithWordOrIs(suffixLessName, "main")) {
        entryPointsBuilder.add(source);
      }
    }
    this.inputs = inputsBuilder.build();
    this.entryPoints = entryPointsBuilder.build();

    final Table<Source, DepType, List<Dep>> depsTable = HashBasedTable.create();
    ImmutableMap.Builder<Source, CssTree> parsedBuilder =
        ImmutableMap.builder();
    boolean parseFailed = false;
    for (Map.Entry<Source, SourceCode> input : inputs.entrySet()) {
      GssParser parser = new GssParser(input.getValue());
      boolean errorHandling = false;
      CssTree parseResult;
      try {
         parseResult = parser.parse(errorHandling);
      } catch (GssParserException ex) {
        log.error("Failed to parse " + input.getKey().canonicalPath, ex);
        parseFailed = true;
        continue;
      }
      parsedBuilder.put(input.getKey(), parseResult);

      final Source src = input.getKey();
      // TODO: also take into account @require & @provide
      // See CheckDependencyNodes pass and CssAtRuleNode.{REQUIRE,PROVIDE}.
      forEachDepRule(
          parseResult.getRoot(),
          new Function<Dep, Void> () {
            @Override
            public Void apply(Dep dep) {
              List<Dep> depList = depsTable.get(src, dep.type);
              if (depList == null) {
                depList = Lists.newArrayList();
                depsTable.put(src, dep.type, depList);
              }
              depList.add(dep);
              return null;
            }
          });
    }
    if (parseFailed) {
      throw new MojoExecutionException(
          "Could not build dependency graph from malformed CSS");
    }
    parsed = parsedBuilder.build();
    deps = ImmutableTable.copyOf(Tables.transformValues(
        depsTable,
        new Function<List<Dep>, ImmutableList<Dep>>() {
          @Override
          public ImmutableList<Dep> apply(List<Dep> ls) {
            return ImmutableList.copyOf(ls);
          }
        }));
    if (log.isDebugEnabled()) {
      StringBuilder sb = new StringBuilder();
      sb.append("CSS dependencies\n");
      for (Table.Cell<Source, DepType, ImmutableList<Dep>> c : deps.cellSet()) {
        sb.append(c.getRowKey().relativePath)
            .append('\t')
            .append(c.getColumnKey())
            .append('\t')
            .append(c.getValue())
            .append('\n');
      }
      log.debug(sb);
    }

    try {
      this.topoSort = new TopoSort<>(
          new Function<Source, Iterable<String>>() {
            @Override
            public Iterable<String> apply(Source s) {
              ImmutableList<Dep> ds = deps.get(s, DepType.REQUIRE);
              if (ds == null) { ds = ImmutableList.of(); }
              return Lists.transform(ds, Dep.GET_SYMBOL);
            }
          },
          new Function<Source, Iterable<String>>() {
            @Override
          public Iterable<String> apply(Source s) {
              ImmutableList<Dep> ds = deps.get(s, DepType.PROVIDE);
              if (ds == null) { ds = ImmutableList.of(); }
              return Lists.transform(ds, Dep.GET_SYMBOL);
            }
          },
          sources);
    } catch (TopoSort.CyclicRequirementException
           | TopoSort.MissingRequirementException ex) {
      throw new MojoExecutionException("Failed to order CSS/GSS files", ex);
    }
  }

  Dependencies transitiveClosureDeps(Source s) {
    ImmutableList<Source> depsInOrder = ImmutableList.<Source>builder()
        .addAll(topoSort.getDependenciesTransitive(s))
        .add(s)
        .build();
    if (depsInOrder != null) {
      return new Dependencies(true, depsInOrder);
    } else {
      return new Dependencies(false, ImmutableList.of(s));
    }
  }

  @SuppressWarnings("static-method")  // Overridable by test harnesses.
  @VisibleForTesting
  protected String loadContent(Source s) throws IOException {
    return Files.toString(s.canonicalPath, Charsets.UTF_8);
  }


  static final class Dependencies {
    final boolean foundAllStatic;
    final ImmutableList<Source> allDependencies;
    Dependencies(
        boolean foundAllStatic, ImmutableList<Source> allDependencies) {
      this.foundAllStatic = foundAllStatic;
      this.allDependencies = allDependencies;
    }
  }


  static final class Dep {
    final DepType type;
    final String symbol;
    final String loc;

    Dep(DepType type, String symbol, SourceCodeLocation loc) {
      this.type = type;
      this.symbol = symbol;
      this.loc = str(loc);
    }

    @Override
    public String toString() {
      return "{" + type + " " + symbol + " @ " + loc + "}";
    }

    static final Function<Dep, String> GET_SYMBOL =
        new Function<Dep, String>() {
          @Override
          public String apply(Dep d) {
            return d.symbol;
          }
        };
  }

  static String str(SourceCodeLocation loc) {
    return loc.getSourceCode().getFileName() + ":"
        + loc.getBeginLineNumber();
  }

  enum DepType {
    PROVIDE,
    REQUIRE,
    ;
  }
}
