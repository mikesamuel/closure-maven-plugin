package com.google.closure.plugin.js;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import org.junit.Test;

import com.google.common.base.Preconditions;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.ImmutableList;
import com.google.closure.plugin.TestLog;
import com.google.closure.plugin.common.SourceOptions.SourceRootBuilder;
import com.google.closure.plugin.js.JsOptions.DependencyMode;
import com.google.closure.plugin.js.JsOptions.FormattingOption;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceMap;
import com.google.javascript.jscomp.WarningLevel;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class JsOptionsTest extends TestCase {

  @Test
  public static void testEmptyToArgv() throws Exception {
    JsOptions opts = new JsOptions();

    assertEquals(ImmutableList.of(), sanityCheckArgv(opts));
  }

  @Test
  public static void testNonFlagOptionsToArgv() throws Exception {
    JsOptions opts = new JsOptions();
    SourceRootBuilder src = new SourceRootBuilder();
    src.set(new File("src"));
    opts.source = new SourceRootBuilder[] { src };

    assertEquals(ImmutableList.of(), sanityCheckArgv(opts));
  }

  @Test
  public static void testEnumToArgv() throws Exception {
    JsOptions opts = new JsOptions();
    opts.compilationLevel = CompilationLevel.ADVANCED_OPTIMIZATIONS;

    assertEquals(
        ImmutableList.of("--compilation_level", "ADVANCED_OPTIMIZATIONS"),
        sanityCheckArgv(opts));
  }

  @Test
  public static void testBooleanToArgv() throws Exception {
    JsOptions opts = new JsOptions();
    opts.checksOnly = true;
    opts.debug = false;

    assertEquals(
        ImmutableList.of("--checks_only", "true", "--debug", "false"),
        sanityCheckArgv(opts));
  }

  public static void testListToArgv() throws Exception {
    JsOptions opts = new JsOptions();
    opts.setDefine("X=0");
    opts.setDefine("Y=1");

    assertEquals(
        ImmutableList.of("--define", "X=0", "--define", "Y=1"),
        sanityCheckArgv(opts));
  }


  @Test
  public static void testThirdParty() throws Exception {
    JsOptions opts = new JsOptions();
    opts.thirdParty = sampleValueFor("thirdParty", Boolean.class);
    sanityCheckArgv(opts);
  }


  @Test
  public static void testSummaryDetailLevel() throws Exception {
    JsOptions opts = new JsOptions();
    opts.summaryDetailLevel = sampleValueFor(
        "summaryDetailLevel", Integer.class);
    sanityCheckArgv(opts);
  }


  @Test
  public static void testOutputWrapper() throws Exception {
    JsOptions opts = new JsOptions();
    opts.outputWrapper = sampleValueFor("outputWrapper", String.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testOutputWrapperFile() throws Exception {
    JsOptions opts = new JsOptions();
    opts.outputWrapperFile = sampleValueFor("outputWrapperFile", String.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testModuleWrapper() throws Exception {
    JsOptions opts = new JsOptions();
    for (String x : sampleValueFor("moduleWrapper", String[].class)) {
      opts.setModuleWrapper(x);
    }

    sanityCheckArgv(opts);
  }


  @Test
  public static void testModuleOutputPathPrefix() throws Exception {
    JsOptions opts = new JsOptions();
    opts.moduleOutputPathPrefix = sampleValueFor(
        "moduleOutputPathPrefix", String.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testSourceMapFormat() throws Exception {
    JsOptions opts = new JsOptions();
    opts.sourceMapFormat = sampleValueFor(
        "sourceMapFormat", SourceMap.Format.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testSourceMapLocationMapping() throws Exception {
    JsOptions opts = new JsOptions();
    for (String x : sampleValueFor(
        "sourceMapLocationMapping", String[].class)) {
      opts.setSourceMapLocationMapping(x);
    }

    sanityCheckArgv(opts);
  }


  @Test
  public static void testSourceMapInputs() throws Exception {
    JsOptions opts = new JsOptions();
    for (String x : sampleValueFor("sourceMapInputs", String[].class)) {
      opts.setSourceMapInputs(x);
    }

    sanityCheckArgv(opts);
  }


  @Test
  public static void testJscompError() throws Exception {
    JsOptions opts = new JsOptions();
    for (String x : sampleValueFor("jscompError", String[].class)) {
      opts.setJscompError(x);
    }

    sanityCheckArgv(opts);
  }


  @Test
  public static void testJscompWarning() throws Exception {
    JsOptions opts = new JsOptions();
    for (String x : sampleValueFor("jscompWarning", String[].class)) {
      opts.setJscompWarning(x);
    }

    sanityCheckArgv(opts);
  }


  @Test
  public static void testJscompOff() throws Exception {
    JsOptions opts = new JsOptions();
    for (String x : sampleValueFor("jscompOff", String[].class)) {
      opts.setJscompOff(x);
    }

    sanityCheckArgv(opts);
  }


  @Test
  public static void testDefine() throws Exception {
    JsOptions opts = new JsOptions();
    for (String x : sampleValueFor("define", String[].class)) {
      opts.setDefine(x);
    }

    sanityCheckArgv(opts);
  }


  @Test
  public static void testCharset() throws Exception {
    JsOptions opts = new JsOptions();
    opts.charset = sampleValueFor("charset", String.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testCompilationLevel() throws Exception {
    JsOptions opts = new JsOptions();
    opts.compilationLevel = sampleValueFor(
        "compilationLevel", CompilationLevel.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testChecksOnly() throws Exception {
    JsOptions opts = new JsOptions();
    opts.checksOnly = sampleValueFor("checksOnly", Boolean.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testUseTypesForOptimization() throws Exception {
    JsOptions opts = new JsOptions();
    opts.useTypesForOptimization = sampleValueFor(
        "useTypesForOptimization", Boolean.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testAssumeFunctionWrapper() throws Exception {
    JsOptions opts = new JsOptions();
    opts.assumeFunctionWrapper = sampleValueFor(
        "assumeFunctionWrapper", Boolean.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testWarningLevel() throws Exception {
    JsOptions opts = new JsOptions();
    opts.warningLevel = sampleValueFor(
        "warningLevel", WarningLevel.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testDebug() throws Exception {
    JsOptions opts = new JsOptions();
    opts.debug = sampleValueFor("debug", Boolean.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testGenerateExports() throws Exception {
    JsOptions opts = new JsOptions();
    opts.generateExports = sampleValueFor("generateExports", Boolean.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testExportLocalPropertyDefinitions() throws Exception {
    JsOptions opts = new JsOptions();
    opts.exportLocalPropertyDefinitions = sampleValueFor(
        "exportLocalPropertyDefinitions", Boolean.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testFormatting() throws Exception {
    JsOptions opts = new JsOptions();
    for (FormattingOption x :
         sampleValueFor("formatting", FormattingOption[].class)) {
      opts.setFormatting(x);
    }

    sanityCheckArgv(opts);
  }


  @Test
  public static void testProcessCommonJsModules() throws Exception {
    JsOptions opts = new JsOptions();
    opts.processCommonJsModules = sampleValueFor(
        "processCommonJsModules", Boolean.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testModuleRoot() throws Exception {
    JsOptions opts = new JsOptions();
    for (String x : sampleValueFor("moduleRoot", String[].class)) {
      opts.setModuleRoot(x);
    }

    sanityCheckArgv(opts);
  }


  @Test
  public static void testTransformAmdModules() throws Exception {
    JsOptions opts = new JsOptions();
    opts.transformAmdModules = sampleValueFor(
        "transformAmdModules", Boolean.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testProcessClosurePrimitives() throws Exception {
    JsOptions opts = new JsOptions();
    opts.processClosurePrimitives = sampleValueFor(
        "processClosurePrimitives", Boolean.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testProcessJqueryPrimitives() throws Exception {
    JsOptions opts = new JsOptions();
    opts.processJqueryPrimitives = sampleValueFor(
        "processJqueryPrimitives", Boolean.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testAngularPass() throws Exception {
    JsOptions opts = new JsOptions();
    opts.angularPass = sampleValueFor("angularPass", Boolean.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testPolymerPass() throws Exception {
    JsOptions opts = new JsOptions();
    opts.polymerPass = sampleValueFor("polymerPass", Boolean.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testDartPass() throws Exception {
    JsOptions opts = new JsOptions();
    opts.dartPass = sampleValueFor("dartPass", Boolean.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testJ2clPass() throws Exception {
    JsOptions opts = new JsOptions();
    opts.j2clPass = sampleValueFor("j2clPass", Boolean.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testOutputManifest() throws Exception {
    JsOptions opts = new JsOptions();
    opts.outputManifest = sampleValueFor("outputManifest", String.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testOutputModuleDependencies() throws Exception {
    JsOptions opts = new JsOptions();
    opts.outputModuleDependencies = sampleValueFor(
        "outputModuleDependencies", String.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testLanguageIn() throws Exception {
    JsOptions opts = new JsOptions();
    opts.languageIn = sampleValueFor(
        "languageIn", CompilerOptions.LanguageMode.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testLanguageOut() throws Exception {
    JsOptions opts = new JsOptions();
    opts.languageOut = sampleValueFor(
        "languageOut", CompilerOptions.LanguageMode.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testVersion() throws Exception {
    JsOptions opts = new JsOptions();
    opts.version = sampleValueFor("version", Boolean.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testTranslationsFile() throws Exception {
    JsOptions opts = new JsOptions();
    opts.translationsFile = sampleValueFor("translationsFile", String.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testTranslationsProject() throws Exception {
    JsOptions opts = new JsOptions();
    opts.translationsProject = sampleValueFor(
        "translationsProject", String.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testFlagFile() throws Exception {
    JsOptions opts = new JsOptions();
    opts.flagFile = sampleValueFor("flagFile", String.class);

    sanityCheckArgv(opts);
  }

  @Test
  public static void testWarningsWhitelistFile() throws Exception {
    JsOptions opts = new JsOptions();
    opts.warningsWhitelistFile = sampleValueFor(
        "warningsWhitelistFile", String.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testHideWarningsFor() throws Exception {
    JsOptions opts = new JsOptions();
    for (String x : sampleValueFor("hideWarningsFor", String[].class)) {
      opts.setHideWarningsFor(x);
    }

    sanityCheckArgv(opts);
  }


  @Test
  public static void testExtraAnnotationName() throws Exception {
    JsOptions opts = new JsOptions();
    for (String x : sampleValueFor("extraAnnotationName", String[].class)) {
      opts.setExtraAnnotationName(x);
    }

    sanityCheckArgv(opts);
  }


  @Test
  public static void testTracerMode() throws Exception {
    JsOptions opts = new JsOptions();
    opts.tracerMode = sampleValueFor(
        "tracerMode", CompilerOptions.TracerMode.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testUseNewTypeInference() throws Exception {
    JsOptions opts = new JsOptions();
    opts.useNewTypeInference = sampleValueFor(
        "useNewTypeInference", Boolean.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testRenamePrefixNamespace() throws Exception {
    JsOptions opts = new JsOptions();
    opts.renamePrefixNamespace = sampleValueFor(
        "renamePrefixNamespace", String.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testConformanceConfigs() throws Exception {
    JsOptions opts = new JsOptions();
    for (String cc : sampleValueFor("conformanceConfigs", String[].class)) {
      opts.setConformanceConfigs(cc);
    }

    sanityCheckArgv(opts);
  }


  @Test
  public static void testEnvironment() throws Exception {
    JsOptions opts = new JsOptions();
    opts.environment = sampleValueFor(
        "environment", CompilerOptions.Environment.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testInstrumentationFile() throws Exception {
    JsOptions opts = new JsOptions();
    opts.instrumentationFile = sampleValueFor(
        "instrumentationFile", String.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testPreserveTypeAnnotations() throws Exception {
    JsOptions opts = new JsOptions();
    opts.preserveTypeAnnotations = sampleValueFor(
        "preserveTypeAnnotations", Boolean.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testInjectLibraries() throws Exception {
    JsOptions opts = new JsOptions();
    opts.injectLibraries = sampleValueFor("injectLibraries", Boolean.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testDependencyMode() throws Exception {
    JsOptions opts = new JsOptions();
    opts.dependencyMode = sampleValueFor(
        "dependencyMode", DependencyMode.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testEntryPoints() throws Exception {
    JsOptions opts = new JsOptions();
    for (String x : sampleValueFor("entryPoints", String[].class)) {
      opts.setEntryPoints(x);
    }

    sanityCheckArgv(opts);
  }


  @Test
  public static void testRewritePolyfills() throws Exception {
    JsOptions opts = new JsOptions();
    opts.rewritePolyfills = sampleValueFor("rewritePolyfills", Boolean.class);

    sanityCheckArgv(opts);
  }


  @Test
  public static void testPrintSourceAfterEachPass() throws Exception {
    JsOptions opts = new JsOptions();
    opts.printSourceAfterEachPass = sampleValueFor(
        "printSourceAfterEachPass", Boolean.class);

    sanityCheckArgv(opts);
  }


  private static final ClassToInstanceMap<Object> SAMPLE_VALUES =
      ImmutableClassToInstanceMap.builder()
      .put(String.class, "foo")
      .put(Boolean.class, true)
      .put(Integer.class, 0)
      .put(String[].class, new String[] { "bar", "baz" })
      .build();

  private static <T> T sampleValueFor(
      @SuppressWarnings("unused") String fieldName, Class<T> type) {
    T fromTable = SAMPLE_VALUES.getInstance(type);
    if (fromTable != null) {
      return fromTable;
    }

    Class<?> componentType = type;
    if (type.isArray()) {
      componentType = type.getComponentType();
    }

    Collection<?> values;
    if (Enum.class.isAssignableFrom(componentType)) {
      @SuppressWarnings("unchecked")
      EnumSet<?> valueSet = EnumSet.allOf(componentType.asSubclass(Enum.class));
      values = valueSet;
    } else if (String.class.equals(componentType)) {
      values = ImmutableList.of("foo", "bar");
    } else if (Boolean.class.equals(componentType)
        || Boolean.TYPE.equals(componentType)) {
      values = ImmutableList.of(false, true);
    } else if (Integer.class.equals(componentType)
        || Integer.TYPE.equals(componentType)) {
      values = ImmutableList.of(0, 1, 2);
    } else {
      throw new AssertionError();
    }

    if (type.isArray()) {
      int n = values.size();
      Object arr = Array.newInstance(componentType, n);
      int i = 0;
      for (Object value : values) {
        Array.set(arr, i++, value);
      }
      Preconditions.checkState(i == n);
      return type.cast(arr);
    }
    return type.cast(values.iterator().next());
  }

  private static ImmutableList<String> sanityCheckArgv(JsOptions opts)
  throws Exception {
    TestLog log = new TestLog();
    ImmutableList<String> argv = opts.toArgv(log);

    Constructor<?> flagsConstructor =
        JsOptions.FieldToFlagMap.FLAGS_CLASS.getDeclaredConstructor();
    Method parseFlagsMethod = JsOptions.FieldToFlagMap.FLAGS_CLASS
        .getDeclaredMethod("parse", List.class);
    flagsConstructor.setAccessible(true);
    parseFlagsMethod.setAccessible(true);

    Object flagsInstance = flagsConstructor.newInstance();
    parseFlagsMethod.invoke(flagsInstance, argv);  // Should error on bad flags.

    return argv;
  }
}
