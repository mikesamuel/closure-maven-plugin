package com.google.closure.plugin.js;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;

import junit.framework.TestCase;

import org.junit.Test;
import org.kohsuke.args4j.Option;

/**
 * Utility that checks that {JsOptions} has the fields required to populate
 * {@link CommandLineRunner}s fields.
 * <p>
 * We use reflection over the fields class to generate an argv for the
 * CommandLineRunner.
 */
public class CheckJsOptionsCompatibleWithCompilerFlagsTest extends TestCase {
  static final ImmutableSet<String> flagBlackList = ImmutableSet.of(
      // Not used.
      "--help", "--print_tree", "--print_ast", "--print_pass_graph",
      "--jscomp_dev_mode", "--dev_mode",

      // Controlled by plugin
      "--logging_level", "--jszip", "--js_output_file", "--module",
      "--variable_renaming_report", "--create_renaming_report",
      "--property_renaming_report", "--js", "--externs", "--json_streams",
      "--create_renaming_reports", "--create_source_map");

  static final ImmutableSet<String> SPECIAL_FIELDS = ImmutableSet.of(
      "source", "testSource", "jsGenfiles", "jsTestGenfiles", "externSource");

  /** Maps class names of non-public flag field types to usable ones. */
  static final ImmutableMap<String, Class<?>> INVISIBLE_NAME_TO_EQUIVALENT =
      ImmutableMap.<String, Class<?>>of(
          CompilerOptions.class.getName() + "$DependencyMode",
          JsOptions.DependencyMode.class,
          CommandLineRunner.class.getName() + "$FormattingOption",
          JsOptions.FormattingOption.class);

  static final ImmutableMap<String, Class<?>> TYPE_SPECIAL_CASES =
      ImmutableMap.<String, Class<?>>of(
          "--compilation_level", CompilationLevel.class,
          "--language_in", CompilerOptions.LanguageMode.class,
          "--language_out", CompilerOptions.LanguageMode.class);


  @SuppressWarnings("javadoc")
  @Test
  public static void testCompatibility() throws Exception {
    Class<?> flagsClass = JsOptions.FieldToFlagMap.FLAGS_CLASS;

    StringBuilder generatedCode = new StringBuilder();
    List<String> problems = Lists.newArrayList();
    Set<String> namesSeen = Sets.newLinkedHashSet();

    for (Field flagsField : flagsClass.getDeclaredFields()) {
      if (Modifier.isStatic(flagsField.getModifiers())) { continue; }
      Option option = flagsField.getAnnotation(Option.class);
      if (option == null) { continue; }
      if (flagBlackList.contains(option.name())) { continue; }
      if (option.usage().contains("Deprecated")) { continue; }
      Type plexusCompatibleType = getPlexusCompatibleType(
          option, flagsField);
      String fieldName = flagsField.getName();

      String usage = option.usage();
      if (usage != null) {
        generatedCode.append(
            "  /** "
            + usage.replace("*/", "*\u2005/").replace("\\", "\\\\")
                .replaceAll("<[^\\s{}]+", "{@code $0}")
            + " */\n");
      }
      String modifiers = "public";
      String typeName = typeName(plexusCompatibleType);
      String initializer = "";
      Type setterInputType = null;

      if (isListType(plexusCompatibleType)) {
        modifiers = "private final";
        initializer = " = Lists.newArrayList()";
        setterInputType = ((ParameterizedType) plexusCompatibleType)
            .getActualTypeArguments()[0];
      }

      if (setterInputType != null) {
        String singularFieldName = singularStem(fieldName);
        String setterName = "set"
            + Character.toUpperCase(singularFieldName.charAt(0))
            + singularFieldName.substring(1);

        generatedCode.append(
            ""
            + "  public void " + setterName
            + "(" + typeName(setterInputType) + " x) {\n"
            + "    // Plexus configurator compatible setter that adds.\n"
            + "    this." + fieldName + ".add(x);\n"
            + "  }\n");

        Class<?> setterInputRawType;
        if (setterInputType instanceof Class<?>) {
          setterInputRawType = (Class<?>) setterInputType;
        } else {
          // Unsound.
          setterInputRawType = (Class<?>)
              ((ParameterizedType) setterInputType).getRawType();
        }

        try {
          JsOptions.class.getMethod(setterName, setterInputRawType);
        } catch (@SuppressWarnings("unused") NoSuchMethodException ex) {
          problems.add(
              flagsField + ":" + option.name()
              + " missing setter " + setterName);
        }
      }

      generatedCode.append(
          "  " + modifiers + " " + typeName + " "
          + fieldName + initializer + ";\n");


      Field correspondingField = null;
      try {
        correspondingField = JsOptions.class.getDeclaredField(fieldName);
      } catch (@SuppressWarnings("unused") NoSuchFieldException ex) {
        problems.add(
            flagsField + ":" + option.name() + " missing field " + fieldName);
      }

      if (correspondingField != null) {
        if (!roughlyEquivalent(
                plexusCompatibleType, correspondingField.getType())) {
          problems.add(
              flagsField + ":" + option.name() + " type mismatch "
              + plexusCompatibleType + " != " + correspondingField.getType());
        }
      }
      namesSeen.add(fieldName);
    }

    Set<String> namesDefined = Sets.newLinkedHashSet();
    for (Field jsOptionsField : JsOptions.class.getDeclaredFields()) {
      if (Modifier.isStatic(jsOptionsField.getModifiers())) { continue; }
      namesDefined.add(jsOptionsField.getName());
    }

    namesDefined.removeAll(SPECIAL_FIELDS);

    if (!problems.isEmpty()) {
      System.out.print(generatedCode);
      fail("mismatched fields " + problems);
    }

    if (!namesSeen.containsAll(namesDefined)) {
      Set<String> extra = Sets.newLinkedHashSet(namesDefined);
      extra.removeAll(namesSeen);

      System.out.print(generatedCode);
      fail("unused fields " + extra);
    }
  }

  private static boolean roughlyEquivalent(Type a, Type b) {
    if (a.equals(b)) {
      return true;
    }

    // We can map enums to strings by name.
    if (a instanceof Class && b instanceof Class) {
      if (String.class.isAssignableFrom((Class<?>) a)) {
        return Enum.class.isAssignableFrom((Class<?>) b);
      }
      if (String.class.isAssignableFrom((Class<?>) b)) {
        return Enum.class.isAssignableFrom((Class<?>) a);
      }
    }

    boolean aIsPt = a instanceof ParameterizedType;
    boolean bIsPt = b instanceof ParameterizedType;
    if (aIsPt != bIsPt) {
      if (aIsPt) {
        return ((ParameterizedType) a).getRawType().equals(b);
      } else {
        return a.equals(((ParameterizedType) b).getRawType());
      }
    }
    return false;
  }

  static Type getPlexusCompatibleType(Option option, Field f) {
    Type fieldType = f.getGenericType();

    Class<?> specialCase = TYPE_SPECIAL_CASES.get(option.name());
    if (specialCase != null) {
      return specialCase;
    }

    if (fieldType instanceof Class<?>) {
      Class<?> typeFromClass = getTypeFromClass((Class<?>) fieldType);
      if (typeFromClass != null) {
        return typeFromClass;
      }
    }
    if (fieldType instanceof ParameterizedType) {
      ParameterizedType pt = (ParameterizedType) fieldType;
      Type rt = pt.getRawType();
      if (rt instanceof Class<?>
          && Collection.class.isAssignableFrom((Class<?>) rt)) {
        Type[] params = pt.getActualTypeArguments();
        if (params.length == 1 && params[0] instanceof Class<?>) {
          Class<?> elementType = getTypeFromClass((Class<?>) params[0]);
          if (elementType != null) {
            return listOfElements(elementType).getType();
          }
        }
      }
    }
    fail("Does not know how to convert " + fieldType
         + ":" + fieldType.getClass());
    return null;
  }

  private static <T>
  TypeToken<List<T>> listOfElements(Class<T> elementType) {
    @SuppressWarnings("serial")
    TypeToken<List<T>> genericList = new TypeToken<List<T>>() {
      // Reifies T
    };
    return genericList.where(
        new TypeParameter<T>() {
          // Reifires T
        }, elementType);
  }

  private static Class<?> getTypeFromClass(Class<?> type) {
    Class<?> equivalent = INVISIBLE_NAME_TO_EQUIVALENT.get(type.getName());
    if (equivalent != null) { return equivalent; }
    if (String.class.equals(type)) {
      return String.class;
    }
    if (Boolean.TYPE.equals(type)) {
      return Boolean.class;  // Tri-state.
    }
    if (Integer.TYPE.equals(type)) {
      return Integer.class;  // null means default
    }
    if (Enum.class.isAssignableFrom(type)) {
      return type;
    }
    return null;
  }

  private static String typeName(Type t) {
    if (t instanceof Class<?>) {
      Class<?> cl = (Class<?>) t;
      if (cl.isPrimitive()) { return cl.getName(); }
      if (cl.isArray()) {
        return typeName(cl.getComponentType()) + "[]";
      }
      String name = cl.getName();
      if (name.startsWith("java.lang.")) {
        String suffix = name.substring("java.lang.".length());
        if (!suffix.contains(".")) {
          return suffix;
        }
      }
      return name;
    } else if (t instanceof ParameterizedType) {
      ParameterizedType pt = (ParameterizedType) t;
      StringBuilder rawTypeName = new StringBuilder(typeName(pt.getRawType()));

      rawTypeName.append('<');

      Type[] params = pt.getActualTypeArguments();
      for (int i = 0, n = params.length; i < n; ++i) {
        if (i != 0) { rawTypeName.append(", "); }
        rawTypeName.append(typeName(params[i]));
      }
      return rawTypeName.append('>').toString();
    } else if (t instanceof WildcardType) {
      throw new AssertionError(t.toString());
    } else if (t instanceof TypeVariable) {
      throw new AssertionError(t.toString());
    } else if (t instanceof GenericArrayType) {
      throw new AssertionError(t.toString());
    } else {
      throw new AssertionError(t.toString() + " : " + t.getClass());
    }
  }

  private static boolean isListType(Type t) {
    Type typ = t;
    if (typ instanceof ParameterizedType) {
      typ = ((ParameterizedType) typ).getRawType();
    }
    return List.class.equals(typ);
  }


  /**
   * Just enough stemming to produce a singular verison of field names so
   * we can do {@code <entryPoint>...</entryPoint>} to configure
   * {@code List<String> entryPoints}.
   */
  private static String singularStem(String name) {
    int nameLen = name.length();
    if (name.endsWith("es") && nameLen > 2) {
      // TODO: Look for a proper english stemmer.
      return name.substring(0, nameLen - 2);
    }
    if (name.endsWith("s") && nameLen > 1) {
      return name.substring(0, nameLen - 1);
    }
    return name;
  }
}
