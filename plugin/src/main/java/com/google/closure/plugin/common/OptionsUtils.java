package com.google.closure.plugin.common;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.google.common.base.Ascii;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * Utilities for dealing with plexus-configurable options bundles.
 */
public final class OptionsUtils {
  private OptionsUtils() {}

  /**
   * Best effort to look up a class and ensures that it is a sub-type of the
   * given type.
   * <p>
   * Some compilers allow plugging-in customizations by providing an instance
   * of an interface.  One way to bridge the gap between that and string flag
   * values or XML plexus configuration elements is by convention -- provide
   * the name of a public concrete class with a public zero-argument constructor
   * that is a sub-type.
   *
   * @param name a qualified class name available from this class's
   *      class loader.
   * @param superType a super-type of the class to load.
   *
   * @return some class when that class can be loaded and obeys the type
   *      constraints above.
   */
  public static <ST>
  Optional<Class<? extends ST>> classForName(
      Log log, String name, Class<ST> superType) {
    ClassLoader cll = OptionsUtils.class.getClassLoader();
    Class<?> cl = null;
    try {
      if (cll != null) {
        cl = cll.loadClass(name);
      } else {
        cl = Class.forName(name);
      }
    } catch (ClassNotFoundException ex) {
      log.error("Failed to load class " + name);
      log.error(ex);
    }
    if (cl != null) {
      if (superType.isAssignableFrom(cl)) {
        return Optional.<Class<? extends ST>>of(cl.asSubclass(superType));
      } else {
        log.error("Loaded class " + name + " is not a subtype of " + superType);
      }
    }
    return Optional.absent();
  }

  /**
   * Best effort to create an instance of the given class using its default
   * constructor without escalating privileges.
   * <p>
   * @return absent if instance creation failed.
   */
  public static <T>
  Optional<T> createInstanceUsingDefaultConstructor(
      Log log, Class<T> superType, Class<?> c) {
    if (!superType.isAssignableFrom(c)) {
      log.error(c.getName() + " is not a sub-type of " + superType.getName());
      return Optional.absent();
    }
    Constructor<? extends T> ctor;
    try {
      // Sound because of isAssignableCheck above.
      @SuppressWarnings("unchecked")
      Constructor<? extends T> tctor =
          (Constructor<? extends T>) c.getConstructor();
      ctor = tctor;
    } catch (NoSuchMethodException ex) {
      log.error("No default constructor for " + c.getName());
      log.error(ex);
      ctor = null;
    }
    if (ctor != null) {
      try {
        T result = ctor.newInstance();
        return Optional.of(superType.cast(result));
      } catch (InvocationTargetException ex) {
        log.error(
            "Failed to create instance of " + c.getName()
            + " using the default constructor");
        log.error(ex);
      } catch (IllegalAccessException ex) {
        log.error(
            "Zero-argument constructor of " + c.getName() + " is not public");
        log.error(ex);
      } catch (InstantiationException ex) {
        log.error(
            "Class " + c.getName() + " is not a concrete instantiable class.");
        log.error(ex);
      }
    }
    return Optional.absent();
  }

  /**
   * Best effort to derive a string to primitive value map from a string of JSON
   * text.
   */
  public static Optional<ImmutableMap<String, Object>> keyValueMapFromJson(
      Log log, String json) {
    ImmutableMap.Builder<String, Object> b = ImmutableMap.builder();
    JSONParser p = new JSONParser();
    Object result;
    try {
      result = p.parse(json);
    } catch (ParseException ex) {
      log.error("Invalid json: " + json);
      log.error(ex);
      return Optional.absent();
    }
    if (result instanceof Map<?, ?>) {
      for (Map.Entry<?, ?> e : ((Map<?, ?>) result).entrySet()) {
        Object k = e.getKey();
        Object v = e.getValue();
        if (!(k instanceof String)) {
          log.error("Bad key " + k + " : " + (k != null ? k.getClass() : null));
          continue;
        }
        if (!(v instanceof Boolean
              || v instanceof Number
              || v instanceof String
              || v == null)) {
          log.error("Value for key " + k + " is not simple : "
              + v + " : " + v.getClass());
          continue;
        }
        b.put((String) k, v);  // TODO: not robust against duplicate keys
      }
    }
    return Optional.of(b.build());
  }

  /**
   * Given a series of options, makes sure that their IDs, and thereby their
   * keys, are unambiguous; their {@link Asplodable} fields are disjoint;
   * and fields with meaningful defaults have those defaults lazily populated.
   */
  public static <OPTIONS extends Options>
  ImmutableList<OPTIONS> prepare(
      Supplier<OPTIONS> makeDefaultInstance,
      Iterable<? extends OPTIONS> optionSets)
  throws MojoExecutionException {
    ImmutableList<OPTIONS> optionSetList = ImmutableList.copyOf(optionSets);
    if (optionSetList.isEmpty()) {
      optionSetList = ImmutableList.of(makeDefaultInstance.get());
    }

    for (OPTIONS o : optionSetList) {
      o.createLazyDefaults();
    }

    optionSetList = asplode(optionSetList);
    disambiguateIds(optionSetList);

    for (OPTIONS o : optionSetList) {
      ImmutableList<? extends Options> subs = o.getSubOptions();
      prepareSubOptions(subs, o);
    }

    return optionSetList;
  }

  private static <SUBOPTIONS extends Options>
  void prepareSubOptions(ImmutableList<SUBOPTIONS> subs, Options parent)
  throws MojoExecutionException {
    if (!subs.isEmpty()) {
      Supplier<SUBOPTIONS> breakingSupplier = newBreakingSupplier();
      parent.setSubOptions(prepare(breakingSupplier, subs));
    }
  }

  private static <OPTIONS extends Options>
  Supplier<OPTIONS> newBreakingSupplier() {
    return new Supplier<OPTIONS>() {
      @Override
      public OPTIONS get() {
        throw new AssertionError("sub options empty");
      }
    };
  }

  private static final Object DEFAULT_VALUE_PLACEHOLDER = new Object() {
    @Override
    public String toString() {
      return "DEFAULT_VALUE_PLACEHOLDER";
    }
  };

  private static final ImmutableSet<Object> DEFAULT_VALUE_SET = ImmutableSet.of(
      DEFAULT_VALUE_PLACEHOLDER);

  static <OPTIONS extends Options>
  ImmutableList<OPTIONS> asplode(ImmutableList<OPTIONS> optionSetList)
  throws MojoExecutionException {
    // Fill in defaults, including any asplodable fields.
    Class<? extends OPTIONS> cl = null;
    for (OPTIONS o : optionSetList) {
      o.createLazyDefaults();
      if (cl == null) {
        // In practice, OPTIONS is not a parameterized type,
        // so this is type-safe.
        @SuppressWarnings("unchecked")
        Class<? extends OPTIONS> oClass =
            (Class<? extends OPTIONS>) o.getClass();
        cl = oClass;
      } else {
        Preconditions.checkArgument(o.getClass() == cl, o.getClass().getName());
      }
    }
    Preconditions.checkNotNull(cl);

    ImmutableList<Field> asplodableFields;
    {
      ImmutableList.Builder<Field> b = ImmutableList.builder();
      for (Field f : cl.getDeclaredFields()) {
        if (f.isAnnotationPresent(Asplodable.class)) {
          Class<?> ft = f.getType();
          Preconditions.checkState(List.class.equals(ft), f.getName());
          b.add(f);
          f.setAccessible(true);
        }
      }
      asplodableFields = b.build();
    }

    ImmutableList<OPTIONS> asplodedOptions;
    if (asplodableFields.isEmpty()) {
      asplodedOptions = optionSetList;
    } else {
      int nAxes = asplodableFields.size();
      ImmutableList.Builder<OPTIONS> asploded = ImmutableList.builder();
      // Asplode each individually.
      for (OPTIONS o : optionSetList) {
        ImmutableList.Builder<Set<Object>> elementSetsBuilder =
            ImmutableList.builder();
        for (int i = 0; i < nAxes; ++i) {
          Field f = asplodableFields.get(i);
          List<?> listOfValues;
          try {
            listOfValues = (List<?>) f.get(o);
          } catch (IllegalAccessException ex) {
            throw (AssertionError)
                new AssertionError("setAccessible").initCause(ex);
          }

          ImmutableSet<Object> elementSet;
          if (listOfValues == null) {
            elementSet = DEFAULT_VALUE_SET;
          } else {
            if (listOfValues.isEmpty()) {
              elementSet = DEFAULT_VALUE_SET;
            } else {
              elementSet = ImmutableSet.copyOf(listOfValues);
            }
          }
          elementSetsBuilder.add(elementSet);
        }
        for (List<Object> asplodedFieldValues
             : Sets.cartesianProduct(elementSetsBuilder.build())) {
          OPTIONS clone;
          try {
            clone = cl.cast(o.clone());
          } catch (CloneNotSupportedException ex) {
            throw new MojoExecutionException(
                "Failed to clone options", ex);
          }
          Preconditions.checkState(nAxes == asplodedFieldValues.size());
          for (int j = 0; j < nAxes; ++j) {
            Field f = asplodableFields.get(j);
            Object value = asplodedFieldValues.get(j);
            try {
              Collection<?> cloneCollection = (Collection<?>) f.get(clone);
              // Check that the collection was actually copied by the clone
              // method.
              Preconditions.checkState(cloneCollection != f.get(o));
              cloneCollection.clear();
              if (value != DEFAULT_VALUE_PLACEHOLDER) {
                // This is safe since the same Field was used to extract the
                // values from the original, and the objects passed have the
                // same unparameterized concrete type.
                addOneValueUNSAFE(cloneCollection, value);
              }
            } catch (IllegalAccessException ex) {
              throw (AssertionError)
              new AssertionError("setAccessible").initCause(ex);
            }
          }
          asploded.add(clone);
        }
      }
      asplodedOptions = asploded.build();
    }
    return asplodedOptions;
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private static void addOneValueUNSAFE(Collection cloneCollection, Object v) {
    cloneCollection.add(v);
  }

  static void disambiguateIds(ImmutableList<? extends Options> optionSetList) {
    int n = optionSetList.size();

    // If an id is unique, then we map it to the index of the option set that
    // has it.  Otherwise we map to -1 to indicate ambiguity.
    Map<String, Integer> ids = new HashMap<>();

    for (int i = 0; i < n; ++i) {
      Options o = optionSetList.get(i);
      String id = o.id;
      if (id != null && !id.isEmpty()) {
        Integer collisionIndex = ids.put(id, i);
        if (collisionIndex != null) {
          ids.put(id, -1);
        }
      }
    }

    // will not be assigned to option sets with missing or ambiguous ids.
    Set<String> noAssign = new HashSet<>();
    for (Map.Entry<String, Integer> e : ids.entrySet()) {
      if (e.getValue().intValue() >= 0) {
        // will not be assigned to option sets with missing or ambiguous ids.
        noAssign.add(e.getKey());
      }
    }

    for (int i = 0; i < n; ++i) {  // HACK: This is O(n**2).
      Options o = optionSetList.get(i);
      String id = o.id;
      if (id == null || id.isEmpty()
          || !Integer.valueOf(i).equals(ids.get(id))) {
        String prefix;
        if (id == null || id.isEmpty()) {
          prefix = o.getClass().getSimpleName();
          int optionsWordIndex = prefix.lastIndexOf("Options");
          if (optionsWordIndex > 0) {
            prefix = prefix.substring(0, optionsWordIndex);
          }
          prefix = Ascii.toLowerCase(prefix);
          o.wasIdImplied = true;
        } else {
          prefix = id;
        }
        id = uniqueIdNotIn(prefix, noAssign);
      }
      o.id = id;
      noAssign.add(id);
    }
  }

  private static String uniqueIdNotIn(
      String prefix, Collection<? extends String> exclusions) {
    StringBuilder sb = new StringBuilder(prefix);
    for (int counter = 0; ; ++counter) {
      sb.setLength(prefix.length());
      sb.append('.').append(counter);
      String candidate = sb.toString();
      if (!exclusions.contains(candidate)) {
        return candidate;
      }
    }
  }

  /**
   * Like {@link #prepare} but accepts a single non-asploding input.
   */
  public static <OPTIONS extends Options> OPTIONS prepareOne(OPTIONS opts)
  throws MojoExecutionException {
    ImmutableList<OPTIONS> prepared = prepare(
        OptionsUtils.<OPTIONS>newBreakingSupplier(),
        ImmutableList.of(opts));
    Preconditions.checkState(prepared.size() == 1, "Expected 1 but asploded");
    return prepared.get(0);
  }
}
