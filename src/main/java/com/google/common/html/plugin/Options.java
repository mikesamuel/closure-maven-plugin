package com.google.common.html.plugin;

import java.io.Serializable;

import com.google.common.html.plugin.plan.Ingredient;

/**
 * Options for a compiler.
 */
public interface Options extends Cloneable, Serializable {

  String getId();

  String getKey();

  Options clone() throws CloneNotSupportedException;
}
