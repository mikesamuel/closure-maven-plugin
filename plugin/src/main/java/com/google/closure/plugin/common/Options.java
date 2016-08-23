package com.google.closure.plugin.common;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import org.apache.maven.plugins.annotations.Parameter;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.closure.plugin.plan.KeyedSerializable;
import com.google.closure.plugin.plan.PlanKey;

/**
 * Options for a compiler.
 */
@SuppressWarnings("serial")  // is abstract
public abstract class Options implements Cloneable, KeyedSerializable {

  /**
   * An ID that must be unique among a bundle of options of the same kind used
   * in a compilation batch.
   * <p>
   * May be null if this has not been disambiguated as per
   * {@link OptionsUtils#disambiguateIds}.
   */
  @Parameter
  String id;

  boolean wasIdImplied;

  /**
   * An ID that must be unique among a bundle of instances of the same kind used
   * in a compilation.
   * <p>
   * May be null if this has not been disambiguated as per
   * {@link OptionsUtils#disambiguateIds}.
   */
  public final String getId() {
    return Preconditions.checkNotNull(id);
  }

  /**
   * True if the ID was set automatically to avoid ambiguity.
   */
  public boolean wasIdImplied() {
    return this.wasIdImplied;
  }

  /**
   * Called after plexus configurations to create defaults for fields that were
   * not supplied by the plexus configurator.
   */
  protected abstract void createLazyDefaults();

  @SuppressWarnings("static-method")
  protected ImmutableList<? extends Options> getSubOptions() {
    return ImmutableList.of();
  }

  /**
   * May be overridden to store the asploded version of {@link #getSubOptions}.
   */
  protected void setSubOptions(
      @SuppressWarnings("unused")
      ImmutableList<? extends Options> preparedSubOptions) {
    throw new UnsupportedOperationException();
  }

  /**
   * A key ingredient that must not overlap with options of a different kind.
   */
  @Override
  public final PlanKey getKey() {
    return PlanKey.builder("opt")
        .addString(getClass().getName())
        .addString(getId())
        .build();
  }

  /**
   * A best-effort copy since options have public immutable fields so that the
   * plexus configurator can muck with them.
   */
  @Override
  public Options clone() throws CloneNotSupportedException {
    try {
      Class<? extends Options> cl = getClass();
      Constructor<? extends Options> ctor = cl.getConstructor();
      Options clone = cl.cast(ctor.newInstance());
      for (Field f : cl.getDeclaredFields()) {
        if (Modifier.isStatic(f.getModifiers())) { continue; }
        Type ft = f.getType();
        Class<?> ct = (Class<?>) (
            (ft instanceof ParameterizedType)
            ? ((ParameterizedType) ft).getRawType()
            : ft);
        if (Collection.class.isAssignableFrom(ct)) {
          copyAllInto(f, clone, this);
        } else {
          f.set(clone, f.get(this));
        }
      }
      return clone;
    } catch (ReflectiveOperationException ex) {
      throw (CloneNotSupportedException)
          new CloneNotSupportedException().initCause(ex);
    }
  }

  private static <T> void copyAllInto(
      final Field f, Object destObj, Object srcObj) {
    Preconditions.checkState(
        destObj.getClass() == srcObj.getClass()
        && destObj.getClass().getTypeParameters().length == 0);
    Function<Object, Collection<T>> getFieldValue =
        new Function<Object, Collection<T>>() {
          // This is actually type-safe because the same field is used for both
          // and the objects have exactly the same unparameterized concrete
          // class.
          @SuppressWarnings("unchecked")
          @Override
          public Collection<T> apply(Object o) {
            try {
              f.setAccessible(true);
              return (Collection<T>) f.get(o);
            } catch (IllegalAccessException ex) {
              throw (AssertionError) new AssertionError("setAccessible")
                  .initCause(ex);
            }
          }
        };
    Collection<T> dest = getFieldValue.apply(destObj);
    Collection<T> src = getFieldValue.apply(srcObj);
    dest.clear();
    dest.addAll(src);
  }
}
