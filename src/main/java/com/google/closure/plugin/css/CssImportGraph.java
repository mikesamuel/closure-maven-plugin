package com.google.closure.plugin.css;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Ascii;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Sets;
import com.google.common.css.SourceCode;
import com.google.common.css.SourceCodeLocation;
import com.google.common.css.compiler.ast.CssFunctionNode;
import com.google.common.css.compiler.ast.CssImportRuleNode;
import com.google.common.css.compiler.ast.CssLiteralNode;
import com.google.common.css.compiler.ast.CssNode;
import com.google.common.css.compiler.ast.CssNodesListNode;
import com.google.common.css.compiler.ast.CssRootNode;
import com.google.common.css.compiler.ast.CssStringNode;
import com.google.common.css.compiler.ast.CssTree;
import com.google.common.css.compiler.ast.CssUnknownAtRuleNode;
import com.google.common.css.compiler.ast.CssValueNode;
import com.google.common.css.compiler.ast.GssParser;
import com.google.common.css.compiler.ast.GssParserException;
import com.google.closure.plugin.common.Words;
import com.google.closure.plugin.common.Sources.Source;
import com.google.common.io.Files;

final class CssImportGraph {
  /** Maps source relative paths to CssCompiler inputs. */
  final ImmutableMap<Source, SourceCode> inputs;
  /** Maps source relative paths to ASTs. */
  final ImmutableMap<Source, CssTree> parsed;
  /** Maps source relative paths to relative paths of imported files. */
  final ImmutableMultimap<Source, Import> deps;
  /** Relative paths of entry style files. */
  final ImmutableList<Source> entryPoints;

  static Optional<Import> importForNode(Log log, CssNode node) {
    List<CssValueNode> parameters = null;
    if (node instanceof CssImportRuleNode) {
      CssImportRuleNode importRuleNode = (CssImportRuleNode) node;
      parameters = importRuleNode.getParameters();
    } else if (node instanceof CssUnknownAtRuleNode) {
      CssUnknownAtRuleNode atRule = (CssUnknownAtRuleNode) node;
      if ("import".equals(atRule.getName().getValue())) {
        parameters = atRule.getParameters();
      }
    }

    if (parameters != null && parameters.size() == 1) {
      // Conditional imports like the media query imports should not be
      // considered for inlining.
      String targetUrl = null;
      CssValueNode targetValue = parameters.get(0);
      if (targetValue instanceof CssStringNode) {
        targetUrl = targetValue.getValue();
      } else if (targetValue instanceof CssFunctionNode) {
        CssFunctionNode fn = (CssFunctionNode) targetValue;
        if ("url".equals(Ascii.toLowerCase(fn.getFunctionName()))) {
          List<CssValueNode> args = fn.getArguments().getChildren();
          if (args.size() == 1) {
            CssValueNode soleArg = args.get(0);
            if (soleArg instanceof CssStringNode
                || soleArg instanceof CssLiteralNode) {
              targetUrl = soleArg.getValue();
            }
          }
        }
      }

      if (targetUrl != null) {
        URI targetUri;
        try {
          targetUri = new URI(targetUrl);
        } catch (URISyntaxException ex) {
          log.error("Bad CSS @import url : `" + targetUrl + "`", ex);
          targetUri = null;
        }
        if (targetUri != null) {
          return Optional.of(
              new Import(targetUri, node.getSourceCodeLocation()));
        }
      }
    }
    return Optional.absent();
  }

  // TODO(mikesamuel): When constructing the import graph, should we skip
  // imports in conditionals or try to interpret the conditionals?
  static void forEachImportRule(
      Log log, CssNode node, Function<? super Import, ?> f) {
    Optional<Import> importFound = importForNode(log, node);
    if (importFound.isPresent()) {
      f.apply(importFound.get());
    }
    if (node instanceof CssNodesListNode<?>) {
      for (CssNode child : ((CssNodesListNode<?>) node).getChildren()) {
        forEachImportRule(log, child, f);
      }
    } else if (node instanceof CssRootNode) {
      forEachImportRule(log, ((CssRootNode) node).getBody(), f);
    }
  }

  CssImportGraph(final Log log, Iterable<? extends Source> sources)
      throws IOException {
    ImmutableMap.Builder<Source, SourceCode> inputsBuilder =
        ImmutableMap.builder();
    ImmutableList.Builder<Source> entryPointsBuilder =
        ImmutableList.builder();
    for (Source source : sources) {
      String fileContent;
      try {
        fileContent = Files.toString(source.canonicalPath, Charsets.UTF_8);
      } catch (IOException ex) {
        log.error("Failed to read " + source.canonicalPath);
        throw ex;
      }
      SourceCode sc = new SourceCode(
          source.relativePath.getPath(), fileContent);
      inputsBuilder.put(source, sc);

      String suffixLessName = source.relativePath.getName()
          .replaceFirst("[.](?:css|gss)\\z", "");
      System.err.println("suffixLessName=" + suffixLessName);
      if (Words.endsWithWordOrIs(suffixLessName, "main")) {
        System.err.println("Found entry point");
        entryPointsBuilder.add(source);
      }
    }
    this.inputs = inputsBuilder.build();
    this.entryPoints = entryPointsBuilder.build();

    final ImmutableMultimap.Builder<Source, Import> depsBuilder =
        ImmutableMultimap.builder();
    ImmutableMap.Builder<Source, CssTree> parsedBuilder =
        ImmutableMap.builder();
    for (Map.Entry<Source, SourceCode> input : inputs.entrySet()) {
      GssParser parser = new GssParser(input.getValue());
      boolean errorHandling = false;
      CssTree parseResult;
      try {
         parseResult = parser.parse(errorHandling);
      } catch (GssParserException ex) {
        throw (AssertionError) (
            new AssertionError(
                "Should not be thrown when errorHandling == false.")
            .initCause(ex));
      }
      parsedBuilder.put(input.getKey(), parseResult);
      System.err.println("looking for imports in " + input.getKey());

      final Source src = input.getKey();
      // TODO: also take into account @require & @provide
      // See CheckDependencyNodes pass and CssAtRuleNode.{REQUIRE,PROVIDE}.
      forEachImportRule(
          log,
          parseResult.getRoot(),
          new Function<Import, Void> () {
            @Override
            public Void apply(Import dep) {
              depsBuilder.put(src, dep);
              return null;
            }
          });
    }
    parsed = parsedBuilder.build();
    deps = depsBuilder.build();
    System.err.println("CSS DEPS\n" + deps + "\n");
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

  Dependencies transitiveClosureDeps(Log log, Source src) {
    Set<Source> out = Sets.newLinkedHashSet();
    boolean foundAll = transitiveClosureDepsOnto(log, src, out);
    return new Dependencies(foundAll, ImmutableList.copyOf(out));
  }

  private boolean transitiveClosureDepsOnto(
      Log log, Source src, Set<Source> srcs) {
    boolean ok = true;
    if (srcs.add(src)) {
      for (Import imp : deps.get(src)) {
        Source target;
        try {
          target = src.resolve(imp.target.getPath());
        } catch (IOException ex) {
          log.error(
              "Cannot resolve dependency of " + src.relativePath
              + " from " + str(imp.loc), ex);
          ok = false;
          continue;
        } catch (URISyntaxException ex) {
            log.error(
                "Cannot resolve dependency of " + src.relativePath
                + " from " + str(imp.loc), ex);
            ok = false;
            continue;
        }
        if (this.parsed.containsKey(target)) {
          transitiveClosureDepsOnto(log, target, srcs);
        } else {
          System.err.println("target=" + target);
          System.err.println("keys=" + this.deps.keySet());
          log.warn(
              "Could not resolve import of " + imp.target + " at "
              + str(imp.loc) + " to a source file");
          ok = false;
        }
      }
    }
    return ok;
  }

  static final class Import {
    final URI target;
    final SourceCodeLocation loc;

    Import(URI target, SourceCodeLocation loc) {
      this.target = target;
      this.loc = loc;
    }

    @Override
    public String toString() {
      return "{Import " + target
          + " @ " + str(loc) + "}";
    }
  }

  static String str(SourceCodeLocation loc) {
    return loc.getSourceCode().getFileName() + ":"
        + loc.getBeginLineNumber();
  }
}
