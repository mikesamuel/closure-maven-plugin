package com.example;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class CompiledJsTest extends TestCase {

  private Context cx;
  private Scriptable scope;

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    cx = Context.enter();
    scope = cx.initSafeStandardObjects();
    // Interpret since compiled JS can blow out the method bytecode limit
    // when compiling to bytecode.
    cx.setOptimizationLevel(-1);
  }

  private void loadJsModule(String moduleResourcePath) throws IOException {
    try (InputStream in = getClass().getResourceAsStream(moduleResourcePath)) {
      Preconditions.checkNotNull(in, "Missing resource " + moduleResourcePath);
      try (Reader code = new InputStreamReader(in, Charsets.UTF_8)) {
        cx.evaluateReader(scope, code, moduleResourcePath, 1, null);
      }
    }
  }

  @Test
  public void testCompiledJs() throws Exception {
    // The main module calls a soy template and alerts the output.
    // Replace alert with something that lets us capture the output.
    cx.evaluateString(
        scope, "var _alerts_ = []; alert = function (s) { _alerts_.push(s); }",
        "test", 1, null);

    loadJsModule("/closure/js/main.js");
    loadJsModule("/closure/js/hello.world.js");

    NativeArray alerts = (NativeArray)
        ScriptableObject.getProperty(scope, "_alerts_");

    assertEquals(
        "Hello, <b>Cle&lt;eland</b>!", Context.toString(alerts.get(0)));
  }
}
