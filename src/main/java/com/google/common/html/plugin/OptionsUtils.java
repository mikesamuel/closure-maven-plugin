package com.google.common.html.plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.maven.plugin.logging.Log;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.google.common.base.Ascii;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public final class OptionsUtils {
  private OptionsUtils() {}

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

  public static boolean splitOnColon(
      Log log, String parameter, String form,
      Function<String[], ?> f,
      String parameterValue) {
    int colon = parameterValue.indexOf(':');
    if (colon < 0) {
      log.error(
          parameter + " `" + parameterValue + "` should have the form " + form);
      return false;
    }
    f.apply(new String[] {
        parameterValue.substring(0, colon),
        parameterValue.substring(colon + 1),
    });
    return true;
  }

  /**
   * Given a series of options, makes sure that their IDs, and thereby their
   * keys, are unambiguous.
   */
  public static void disambiguateIds(Iterable<? extends Options> optionSets) {
    ImmutableList<Options> optionSetList = ImmutableList.copyOf(optionSets);
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
}
