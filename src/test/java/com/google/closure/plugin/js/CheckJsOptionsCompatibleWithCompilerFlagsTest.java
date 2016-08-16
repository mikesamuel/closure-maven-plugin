package com.google.closure.plugin.js;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
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
      "--property_renaming_report", "--js", "--externs", "--json_streams");

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
    List<Field> problems = Lists.newArrayList();
    Set<String> namesSeen = Sets.newLinkedHashSet();

    for (Field flagsField : flagsClass.getDeclaredFields()) {
      if (Modifier.isStatic(flagsField.getModifiers())) { continue; }
      Option option = flagsField.getAnnotation(Option.class);
      if (option == null) { continue; }
      if (flagBlackList.contains(option.name())) { continue; }
      if (option.usage().contains("Deprecated")) { continue; }
      Class<?> plexusCompatibleType = getPlexusCompatibleType(
          option, flagsField);
      String fieldName = flagsField.getName();

      String usage = option.usage();
      if (usage != null) {
        generatedCode.append(
            "    /** "
            + usage.replace("*/", "*\u2005/").replace("\\", "\\\\")
                .replaceAll("<[^\\s{}]+", "{@code $0}")
            + " */\n");
      }
      generatedCode.append(
          "    public " + typeName(plexusCompatibleType) + " "
          + fieldName + ";\n");

      Field correspondingField = null;
      try {
        correspondingField = JsOptions.class.getField(fieldName);
      } catch (@SuppressWarnings("unused") NoSuchFieldException ex) {
        problems.add(flagsField);
      }

      if (correspondingField != null) {
        if (!plexusCompatibleType.equals(correspondingField.getType())) {
          problems.add(flagsField);
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

  static Class<?> getPlexusCompatibleType(Option option, Field f) {
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
            return Array.newInstance(elementType, 0).getClass();
          }
        }
      }
    }
    fail("Does not know how to convert " + fieldType
         + ":" + fieldType.getClass());
    return null;
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

  private static String typeName(Class<?> cl) {
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
  }
}
