package com.google.common.html.plugin.js;

import java.io.Serializable;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

abstract class Identifier
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
