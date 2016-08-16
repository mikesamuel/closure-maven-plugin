package com.google.common.html.plugin.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the annotated {@link Options} field is an array field that
 * should have, at most, one value after the options have been asploded into
 * clones that have every possible combination of asplodable field values from
 * the ambiguous originating option set.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Asplodable {
  // this left intentionally blank.
}
