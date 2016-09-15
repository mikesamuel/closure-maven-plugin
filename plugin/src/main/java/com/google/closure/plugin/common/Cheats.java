package com.google.closure.plugin.common;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Some projects use package-private APIs to programmaticly build compile
 * configurations.
 * I prefer programmatic APIs to building flag lists, so use reflection in
 * dodgy ways while I negotiate programmatic APIs and push them into central.
 *
 * TODO: push changes back into the main projects until this is unnecessary.
 */
public final class Cheats {

  /**
   * Escalates privileges to call the named method.
   *
   * @param argumentTypesAndArgs [erasedType0, arg0, erasedType1, arg1, ...]
   */
  public static <TT, RT> RT cheatCall(
      Class<RT> returnType,
      Class<TT> thisType, TT thisValue,
      String methodName,
      Object... argumentTypesAndArgs)
  throws InvocationTargetException {
    int nArgs = argumentTypesAndArgs.length / 2;
    assert nArgs * 2 == argumentTypesAndArgs.length;
    Class<?>[] argumentTypes = new Class[nArgs];
    Object[] arguments = new Object[nArgs];
    for (int i = 0; i < nArgs; ++i) {
      argumentTypes[i] = (Class<?>) argumentTypesAndArgs[i * 2];
      arguments[i] = argumentTypesAndArgs[i * 2 + 1];
    }
    // Cheat by using private fields.
    // TODO(mikesamuel): Submit a patch to JSCompiler to make these public.

    Method m;
    try {
      m = thisType.getDeclaredMethod(methodName, argumentTypes);
    } catch (NoSuchMethodException ex) {
      throw (NoSuchMethodError) new NoSuchMethodError().initCause(ex);
    }
    assert returnType == m.getReturnType();

    if (!Modifier.isPublic(m.getModifiers())) {
      m.setAccessible(true);
    }
    try {
      return returnType.cast(m.invoke(thisValue, arguments));
    } catch (IllegalAccessException ex) {
      throw new AssertionError("set accessible to true", ex);
    }
  }

  /** Escalates privileges to set a field. */
  public static <TT>
  void cheatSet(
      Class<TT> thisType, TT thisValue, String fieldName, Object newValue) {
    Field f;
    try {
      f = thisType.getDeclaredField(fieldName);
    } catch (NoSuchFieldException ex) {
      throw (NoSuchFieldError) new NoSuchFieldError().initCause(ex);
    }
    if (!Modifier.isPublic(f.getModifiers())) {
      f.setAccessible(true);
    }
    try {
      f.set(thisValue, newValue);
    } catch (IllegalAccessException ex) {
      throw new AssertionError("set accessible to true", ex);
    }
  }

  /** Escalates privileges to read a field. */
  public static <TT, FT>
  FT cheatGet(
      Class<TT> thisType, TT thisValue, Class<FT> fieldType, String fieldName) {
    Field f;
    try {
      f = thisType.getDeclaredField(fieldName);
    } catch (NoSuchFieldException ex) {
      throw (NoSuchFieldError) new NoSuchFieldError().initCause(ex);
    }
    if (!Modifier.isPublic(f.getModifiers())) {
      f.setAccessible(true);
    }
    try {
      return fieldType.cast(f.get(thisValue));
    } catch (IllegalAccessException ex) {
      throw new AssertionError("set accessible to true", ex);
    }
  }

}
