package com.google.closure.plugin.css;

import java.io.Serializable;

import com.google.common.collect.ImmutableList;

final class CssBundleList implements Serializable {
  private static final long serialVersionUID = -3234002453435104079L;

  final ImmutableList<CssBundle> bundles;

  CssBundleList(Iterable<? extends CssBundle> bundles) {
    this.bundles = ImmutableList.copyOf(bundles);
  }
}
