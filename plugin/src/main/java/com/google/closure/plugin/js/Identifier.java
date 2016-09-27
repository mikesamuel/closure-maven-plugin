package com.google.closure.plugin.js;

import java.io.Serializable;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

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
    return o != null && o.getClass() == getClass() && text.equals(((Identifier) o).text);
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
    static final ModuleName DEFAULT_MAIN_MODULE_NAME = new ModuleName("main");
    static final ModuleName DEFAULT_TEST_MODULE_NAME = new ModuleName("test");

    ModuleName(String text) {
      super(text);
    }
  }

  static final class GoogNamespace extends Identifier {
    private static final long serialVersionUID = -4018457478547773405L;

    GoogNamespace(String text) {
      super(text);
    }

    static CharSequence shortLogForm(
        Iterable<? extends GoogNamespace> namespaces) {

      final class NsTrie {
        final String name;
        final Map<String, NsTrie> children = Maps.newLinkedHashMap();

        NsTrie(String name) {
          this.name = name;
        }

        void add(int idx, GoogNamespace ns) {
          String text = ns.text;
          Preconditions.checkState(idx == -1 || text.charAt(idx) == '.');
          int nextDot = text.indexOf('.', idx + 1);
          if (nextDot < 0) { nextDot = text.length(); }
          String part = text.substring(idx + 1, nextDot);
          NsTrie child = children.get(part);
          if (child == null) {
            children.put(part, child = new NsTrie(part));
          }
          if (nextDot < text.length()) {
            child.add(nextDot, ns);
          }
        }

        public void appendTo(StringBuilder sb) {
          sb.append(name);
          switch (children.size()) {
            case 0:
              break;
            case 1:
              children.values().iterator().next()
              .appendTo(sb.append('.'));
              break;
            default:
              sb.append(".{");
              boolean first = true;
              for (NsTrie child : children.values()) {
                if (!first) { sb.append(','); }
                first = false;
                child.appendTo(sb);
              }
              sb.append('}');
              break;
          }
        }
      }

      NsTrie root = new NsTrie(null);
      for (GoogNamespace ns : namespaces) {
        root.add(-1, ns);
      }
      StringBuilder sb = new StringBuilder();
      for (NsTrie child : root.children.values()) {
        if (sb.length() != 0) { sb.append(", "); }
        child.appendTo(sb);
      }
      return sb;
    }
  }

}
