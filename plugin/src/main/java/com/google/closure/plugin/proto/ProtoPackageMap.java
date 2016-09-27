package com.google.closure.plugin.proto;

import java.util.Iterator;
import java.util.Map;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.closure.plugin.common.CStyleLexer;
import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.plan.BundlingPlanGraphNode.Bundle;
import com.google.closure.plugin.plan.Metadata;

/** Relates proto input files to their package declarations. */
public final class ProtoPackageMap implements Bundle {
  private static final long serialVersionUID = 7002905141041309585L;

  /** Empty instance. */
  public static final ProtoPackageMap EMPTY = new ProtoPackageMap(
      ImmutableMap.<Source, Metadata<Optional<String>>>of());

  /** Relates proto input files to their package declarations. */
  public final ImmutableMap<Source, Metadata<Optional<String>>> protoPackages;

  ProtoPackageMap(
      Map<? extends Source, ? extends Metadata<Optional<String>>>
          protoPackages) {
    this.protoPackages = ImmutableMap.copyOf(protoPackages);
  }

  /**
   * Extracts the package from a.proto definition.
   */
  public static Optional<String> getPackage(CStyleLexer lex) {
    String packageName = null;
    Iterator<CStyleLexer.Token> it = lex.iterator();
    while (it.hasNext()) {
      CStyleLexer.Token t = it.next();
      if (t.type == CStyleLexer.TokenType.WORD && t.hasText("package")) {
        if (it.hasNext()) {
          t = it.next();
          if (t.type == CStyleLexer.TokenType.WORD) {
            StringBuilder sb = new StringBuilder(t.toString());
            while (it.hasNext() && it.next().hasText(".")) {
              if (it.hasNext()) {
                t = it.next();
                if (t.type == CStyleLexer.TokenType.WORD) {
                  sb.append('.').append(t.toString());
                } else {
                  sb.setLength(0);
                  break;
                }
              }
            }
            packageName = sb.toString();
          }
        }
      }
    }
    return Optional.fromNullable(packageName);
  }

  @Override
  public ImmutableSet<Source> getInputs() {
    return protoPackages.keySet();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((protoPackages == null) ? 0 : protoPackages.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    ProtoPackageMap other = (ProtoPackageMap) obj;
    if (protoPackages == null) {
      if (other.protoPackages != null) {
        return false;
      }
    } else if (!protoPackages.equals(other.protoPackages)) {
      return false;
    }
    return true;
  }
}
