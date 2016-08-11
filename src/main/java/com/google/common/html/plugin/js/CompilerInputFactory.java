package com.google.common.html.plugin.js;

import java.io.IOException;

import com.google.common.html.plugin.common.Sources.Source;
import com.google.common.html.plugin.plan.Hash;
import com.google.javascript.jscomp.CompilerInput;

interface CompilerInputFactory {
  CompilerInput create(Source source);
  Hash hash(Source source) throws IOException;
}
