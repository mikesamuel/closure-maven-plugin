package com.google.common.html.plugin.js;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.apache.maven.plugin.logging.Log;
import org.kohsuke.args4j.Option;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.html.plugin.common.SourceOptions;
import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceMap;
import com.google.javascript.jscomp.WarningLevel;

/**
 * A plexus-configurable set of options compatible with
 * {@link CommandLineRunner}.
 */
public final class JsOptions extends SourceOptions {

  private static final long serialVersionUID = -5477807829203040714L;

  /**
   * Directory for generated production JS source files.
   */
  public File jsGenfiles;

  /**
   * Directory for generated test-only JS source files.
   */
  public File jsTestGenfiles;

  /** If true, variable renaming and property renaming report files will be produced as {binary name}_vars_renaming_report.out and {binary name}_props_renaming_report.out. Note that this flag cannot be used in conjunction with either variable_renaming_report or property_renaming_report */
  public Boolean createNameMapFiles;
  /** Check source validity but do not enforce Closure style rules and conventions */
  public Boolean thirdParty;
  /** Controls how detailed the compilation summary is. Values: 0 (never print summary), 1 (print summary only if there are errors or warnings), 2 (print summary if the 'checkTypes' diagnostic  group is enabled, see --jscomp_warning), 3 (always print summary). The default level is 1 */
  public Integer summaryDetailLevel;
  /** Interpolate output into this string at the place denoted by the marker token %output%. Use marker token %output|jsstring% to do js string escaping on the output. */
  public String outputWrapper;
  /** Loads the specified file and passes the file contents to the --output_wrapper flag, replacing the value if it exists. This is useful if you want special characters like newline in the wrapper. */
  public String outputWrapperFile;
  /** An output wrapper for a JavaScript module (optional). The format is {@code <name>:<wrapper>.} The module name must correspond with a module specified using --module. The wrapper must contain %s as the code placeholder. The %basename% placeholder can also be used to substitute the base name of the module output file. */
  public String[] moduleWrapper;
  /** Prefix for filenames of compiled JS modules. {@code <module-name>.js} will be appended to this prefix. Directories will be created as needed. Use with --module */
  public String moduleOutputPathPrefix;
  /** If specified, a source map file mapping the generated source files back to the original source file will be output to the specified path. The %outname% placeholder will expand to the name of the output file that the source map corresponds to. */
  public String createSourceMap;
  /** The source map format to produce. Options are V3 and DEFAULT, which are equivalent. */
  public SourceMap.Format sourceMapFormat;
  /** Source map location mapping separated by a '|' (i.e. filesystem-path|webserver-path) */
  public String[] sourceMapLocationMapping;
  /** Source map locations for input files, separated by a '|', (i.e. input-file-path|input-source-map) */
  public String[] sourceMapInputs;
  /** Make the named class of warnings an error. Must be one of the error group items. '*' adds all supported. */
  public String[] jscompError;
  /** Make the named class of warnings a normal warning. Must be one of the error group items. '*' adds all supported. */
  public String[] jscompWarning;
  /** Turn off the named class of warnings. Must be one of the error group items. '*' adds all supported. */
  public String[] jscompOff;
  /** Override the value of a variable annotated @define. The format is {@code <name>[=<val>],} where {@code <name>} is the name of a @define variable and {@code <val>} is a boolean, number, or a single-quoted string that contains no single quotes. If [={@code <val>]} is omitted, the variable is marked true */
  public String[] define;
  /** Input and output charset for all files. By default, we accept UTF-8 as input and output US_ASCII */
  public String charset;
  /** Specifies the compilation level to use. Options: WHITESPACE_ONLY, SIMPLE, ADVANCED */
  public CompilationLevel compilationLevel;
  /** Don't generate output. Run checks, but no optimization passes. */
  public Boolean checksOnly;
  /** Enable or disable the optimizations based on available type information. Inaccurate type annotations may result in incorrect results. */
  public Boolean useTypesForOptimization;
  /** Enable additional optimizations based on the assumption that the output will be wrapped with a function wrapper.  This flag is used to indicate that "global" declarations will not actually be global but instead isolated to the compilation unit. This enables additional optimizations. */
  public Boolean assumeFunctionWrapper;
  /** Specifies the warning level to use. Options: QUIET, DEFAULT, VERBOSE */
  public WarningLevel warningLevel;
  /** Enable debugging options */
  public Boolean debug;
  /** Generates export code for those marked with @export */
  public Boolean generateExports;
  /** Generates export code for local properties marked with @export */
  public Boolean exportLocalPropertyDefinitions;
  /** Specifies which formatting options, if any, should be applied to the output JS. Options: PRETTY_PRINT, PRINT_INPUT_DELIMITER, SINGLE_QUOTES */
  public FormattingOption[] formatting;
  /** Process CommonJS modules to a concatenable form. */
  public Boolean processCommonJsModules;
  /** Path prefixes to be removed from ES6 & CommonJS modules. */
  public String[] moduleRoot;
  /** Transform AMD to CommonJS modules. */
  public Boolean transformAmdModules;
  /** Processes built-ins from the Closure library, such as goog.require(), goog.provide(), and goog.exportSymbol(). True by default. */
  public Boolean processClosurePrimitives;
  /** Processes built-ins from the Jquery library, such as jQuery.fn and jQuery.extend() */
  public Boolean processJqueryPrimitives;
  /** Generate $inject properties for AngularJS for functions annotated with @ngInject */
  public Boolean angularPass;
  /** Rewrite Polymer classes to be compiler-friendly. */
  public Boolean polymerPass;
  /** Rewrite Dart Dev Compiler output to be compiler-friendly. */
  public Boolean dartPass;
  /** Rewrite J2CL output to be compiler-friendly. */
  public Boolean j2clPass;
  /** Prints out a list of all the files in the compilation. If --dependency_mode=STRICT or LOOSE is specified, this will not include files that got dropped because they were not required. The %outname% placeholder expands to the JS output file. If you're using modularization, using %outname% will create a manifest for each module. */
  public String outputManifest;
  /** Prints out a JSON file of dependencies between modules. */
  public String outputModuleDependencies;
  /** Sets what language spec that input sources conform. Options: ECMASCRIPT3, ECMASCRIPT5, ECMASCRIPT5_STRICT, ECMASCRIPT6 (default), ECMASCRIPT6_STRICT, ECMASCRIPT6_TYPED (experimental) */
  public CompilerOptions.LanguageMode languageIn;
  /** Sets what language spec the output should conform to. Options: ECMASCRIPT3 (default), ECMASCRIPT5, ECMASCRIPT5_STRICT, ECMASCRIPT6_TYPED (experimental) */
  public CompilerOptions.LanguageMode languageOut;
  /** Prints the compiler version to stdout and exit. */
  public Boolean version;
  /** Source of translated messages. Currently only supports XTB. */
  public String translationsFile;
  /** Scopes all translations to the specified project.When specified, we will use different message ids so that messages in different projects can have different translations. */
  public String translationsProject;
  /** A file containing additional command-line options. */
  public String flagFile;
  /** A file containing warnings to suppress. Each line should be of the form
{@code <file-name>:<line-number>?}  {@code <warning-description>} */
  public String warningsWhitelistFile;
  /** If specified, files whose path contains this string will have their warnings hidden. You may specify multiple. */
  public String[] hideWarningsFor;
  /** A whitelist of tag names in JSDoc. You may specify multiple */
  public String[] extraAnnotationName;
  /** Shows the duration of each compiler pass and the impact to the compiled output size. Options: ALL, RAW_SIZE, TIMING_ONLY, OFF */
  public CompilerOptions.TracerMode tracerMode;
  /** Checks for type errors using the new type inference algorithm. */
  public Boolean useNewTypeInference;
  /** Specifies the name of an object that will be used to store all non-extern globals */
  public String renamePrefixNamespace;
  /** A list of JS Conformance configurations in text protocol buffer format. */
  public String[] conformanceConfigs;
  /** Determines the set of builtin externs to load. Options: BROWSER, CUSTOM. Defaults to BROWSER. */
  public CompilerOptions.Environment environment;
  /** A file containing an instrumentation template. */
  public String instrumentationFile;
  /** Preserves type annotations. */
  public Boolean preserveTypeAnnotations;
  /** Allow injecting runtime libraries. */
  public Boolean injectLibraries;
  /** Specifies how the compiler should determine the set and order of files for a compilation. Options: NONE the compiler will include all src files in the order listed, STRICT files will be included and sorted by starting from namespaces or files listed by the --entry_point flag - files will only be included if they are referenced by a goog.require or CommonJS require or ES6 import, LOOSE same as with STRICT but files which do not goog.provide a namespace and are not modules will be automatically added as --entry_point entries. Defaults to NONE. */
  public DependencyMode dependencyMode;
  /** A file or namespace to use as the starting point for determining which src files to include in the compilation. ES6 and CommonJS modules are specified as file paths (without the extension). Closure-library namespaces are specified with a "goog:" prefix. Example: --entry_point=goog:goog.Promise */
  public String[] entryPoints;
  /** Rewrite ES6 library calls to use polyfills provided by the compiler's runtime. */
  public Boolean rewritePolyfills;
  /** Whether to iteratively print resulting JS source per pass. */
  public Boolean printSourceAfterEachPass;

  @Override
  protected void createLazyDefaults() {
    // Done
  }

  /** Proxy for {@link CommandLineRunner}.FormattingOptions. */
  public enum FormattingOption {
    /** Output should include properly indented white-space. */
    PRETTY_PRINT,
    /** TODO */
    PRINT_INPUT_DELIMITER,
    /**
     * Output should prefer single quotes to double quotes for quoted strings
     * to enable easy embedding in double-quoted attribute values.
     */
    SINGLE_QUOTES,
    ;
  }

  /** Proxy for {@link CompilerOptions}.DependencyMode. */
  public enum DependencyMode {
    /**
     * All files will be included in the compilation
     */
    NONE,

    /**
     * Files must be discoverable from specified entry points. Files
     * which do not goog.provide a namespace and and are not either
     * an ES6 or CommonJS module will be automatically treated as entry points.
     * Module files will be included only if referenced from an entry point.
     */
    LOOSE,

    /**
     * Files must be discoverable from specified entry points. Files which
     * do not goog.provide a namespace and are neither
     * an ES6 or CommonJS module will be dropped. Module files will be included
     * only if referenced from an entry point.
     */
    STRICT,
  }

  /**
   * A list of command line flags that can be fed to
   * {@link CommandLineRunner#run}.
   */
  public ImmutableList<String> toArgv(Log log) {
    ImmutableList.Builder<String> argv = ImmutableList.builder();
    addArgv(log, argv);
    return argv.build();
  }

  /**
   * Appends command line flags that can be fed to
   * {@link CommandLineRunner#run}.
   * <p>
   * This is done because there is a whole lot of compiler machinery
   * that is available via the CommandLineRunner that is not available via
   * {@link CompilerOptions}.
   * I would prefer to use the latter programmatically.
   */
  public void addArgv(
      @SuppressWarnings("unused") Log log,
      ImmutableList.Builder<String> argv) {
    for (Field f : JsOptions.class.getFields()) {
      if (Modifier.isStatic(f.getModifiers())) { continue; }
      String flagName = FieldToFlagMap.FIELD_TO_FLAG.get(f.getName());
      if (flagName == null) { continue; }
      Object value;
      try {
        value = f.get(this);
      } catch (IllegalAccessException ex) {
        // All flag fields should be public since they are de-facto part of
        // the public API via the plexus configurator.
        throw (AssertionError) new AssertionError(
            "Field for flag " + flagName + " should be public")
            .initCause(ex);
      }
      if (value == null) { continue; }
      if (value.getClass().isArray()) {
        int n = Array.getLength(value);
        for (int i = 0; i < n; ++i) {
          addFlag(flagName, f, Array.get(value, i), argv);
        }
      } else if (value instanceof Iterable<?>) {
        for (Object oneValue : (Iterable<?>) value) {
          addFlag(flagName, f, oneValue, argv);
        }
      } else {
        addFlag(flagName, f, value, argv);
      }
    }
  }

  /** Does just enough to enable parsing of source files. */
  public CompilerOptions toCompilerOptions() {
    CompilerOptions compilerOptions = new CompilerOptions();
    if (this.languageIn != null) {
      compilerOptions.setLanguageIn(this.languageIn);
    }
    if (this.languageOut != null) {
      compilerOptions.setLanguageOut(this.languageOut);
    }
    return compilerOptions;
  }

  private static void addFlag(
      String name, Field f, Object value, ImmutableList.Builder<String> argv) {
    if (false && value instanceof Boolean) {
      argv.add(
          ((Boolean) value).booleanValue()
          ? name
          : name.replaceFirst("[^\\-]", "no$0"));  // --debug -> --nodebug
      return;
    }

    String valueStr;
    if (value instanceof CharSequence
        || value instanceof Boolean
        || value instanceof Number) {
      valueStr = value.toString();
    } else if (value instanceof Enum<?>) {
      valueStr = ((Enum<?>) value).name();
    } else if (value instanceof Class<?>) {
      valueStr = ((Class<?>) value).getName();
    } else {
      throw new IllegalArgumentException(
          "Don't know how to convert " + value + " : " + value.getClass()
          + " obtained from " + f.getDeclaringClass() + "." + f.getName()
          + " to value for flag " + name);
    }
    argv.add(name).add(valueStr);
  }


  static class FieldToFlagMap {
    static final Class<?> FLAGS_CLASS;
    static final ImmutableMap<String, String> FIELD_TO_FLAG;

    static {
      ClassLoader cl = CommandLineRunner.class.getClassLoader();
      if (cl == null) { cl = ClassLoader.getSystemClassLoader(); }
      Class<?> flagsClass = null;
      try {
        flagsClass = cl.loadClass(CommandLineRunner.class.getName() + "$Flags");
      } catch (ReflectiveOperationException ex) {
        throw (AssertionError)
            new AssertionError("Missing flags class").initCause(ex);
      }
      FLAGS_CLASS = flagsClass;
      ImmutableMap.Builder<String, String> b = ImmutableMap.builder();
      for (Field flagsField : FLAGS_CLASS.getDeclaredFields()) {
        if (Modifier.isStatic(flagsField.getModifiers())) { continue; }
        Option option = flagsField.getAnnotation(Option.class);
        if (option == null) { continue; }
        b.put(flagsField.getName(), option.name());
      }
      FIELD_TO_FLAG = b.build();
    }
  }


  @Override
  protected ImmutableList<String> sourceExtensions() {
    return ImmutableList.of("js", "ts");
  }
}
