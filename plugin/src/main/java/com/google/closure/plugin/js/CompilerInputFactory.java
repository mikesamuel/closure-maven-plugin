package com.google.closure.plugin.js;

import com.google.closure.plugin.common.Sources.Source;
import com.google.closure.plugin.plan.Hash;
import com.google.javascript.jscomp.CompilerInput;

interface CompilerInputFactory {
  CompilerInput create(Source source);
  Hash hash(Source source);
}
