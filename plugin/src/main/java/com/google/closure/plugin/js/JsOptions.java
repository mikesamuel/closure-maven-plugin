package com.google.closure.plugin.js;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import org.apache.maven.plugin.logging.Log;
import org.kohsuke.args4j.Option;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.closure.plugin.common.FileExt;
import com.google.closure.plugin.common.SourceOptions;
import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceMap;

/**
 * A plexus-configurable set of options compatible with
 * {@link CommandLineRunner}.
 */
public final class JsOptions extends SourceOptions {

  private static final long serialVersionUID = -5477807829203040714L;

  /**
   * Check source validity but do not enforce Closure style rules and
   * conventions
   */
  public Boolean thirdParty;
  /** Controls how detailed the compilation summary is. Values: 0
   * (never print summary), 1 (print summary only if there are errors
   * or warnings), 2 (print summary if the 'checkTypes' diagnostic
   * group is enabled, see --jscomp_warning), 3 (always print
   * summary). The default level is 1 */
  public Integer summaryDetailLevel;
  /**
   * Interpolate output into this string at the place denoted by the
   * marker token %output%. Use marker token %output|jsstring% to do
   * js string escaping on the output.
   */
  public String outputWrapper;
  /**
   * Loads the specified file and passes the file contents to the
   * --output_wrapper flag, replacing the value if it exists. This is
   * useful if you want special characters like newline in the
   * wrapper.
   */
  public String outputWrapperFile;
  /**
   * An output wrapper for a JavaScript module (optional). The format
   * is {@code <name>:<wrapper>.} The module name must correspond with
   * a module specified using --module. The wrapper must contain %s as
   * the code placeholder. The %basename% placeholder can also be used
   * to substitute the base name of the module output file.
   */
  public void setModuleWrapper(String x) {
    // Plexus configurator compatible setter that adds.
    this.moduleWrapper.add(x);
  }
  private final List<String> moduleWrapper = Lists.newArrayList();
  /**
   * Prefix for filenames of compiled JS modules.
   * {@code <module-name>.js} will be appended to this prefix.
   * Directories will be created as needed. Use with --module
   */
  public String moduleOutputPathPrefix;
  /**
   * The source map format to produce. Options are V3 and DEFAULT,
   * which are equivalent.
   */
  public SourceMap.Format sourceMapFormat;
  /**
   * Source map location mapping separated by a '|'
   * (i.e. filesystem-path|webserver-path)
   */
  public void setSourceMapLocationMapping(String x) {
    // Plexus configurator compatible setter that adds.
    this.sourceMapLocationMapping.add(x);
  }
  private final List<String> sourceMapLocationMapping = Lists.newArrayList();
  /**
   * Source map locations for input files, separated by a '|',
   * (i.e. input-file-path|input-source-map)
   */
  public void setSourceMapInput(String x) {
    // Plexus configurator compatible setter that adds.
    this.sourceMapInputs.add(x);
  }
  private final List<String> sourceMapInputs = Lists.newArrayList();
  /**
   * Make the named class of warnings an error. Must be one of the
   * error group items. '*' adds all supported.
   */
  public void setJscompError(String x) {
    // Plexus configurator compatible setter that adds.
    this.jscompError.add(x);
  }
  private final List<String> jscompError = Lists.newArrayList();
  /**
   * Make the named class of warnings a normal warning. Must be one
   * of the error group items. '*' adds all supported.
   */
  public void setJscompWarning(String x) {
    // Plexus configurator compatible setter that adds.
    this.jscompWarning.add(x);
  }
  private final List<String> jscompWarning = Lists.newArrayList();
  /**
   * Turn off the named class of warnings. Must be one of the error
   * group items. '*' adds all supported.
   */
  public void setJscompOff(String x) {
    // Plexus configurator compatible setter that adds.
    this.jscompOff.add(x);
  }
  private final List<String> jscompOff = Lists.newArrayList();
  /**
   * Override the value of a variable annotated @define. The format is
   * {@code <name>[=<val>],} where {@code <name>} is the name of
   * a @define variable and {@code <val>} is a boolean, number, or a
   * single-quoted string that contains no single quotes.
   * If [={@code <val>]} is omitted, the variable is marked true
   */
  public void setDefine(String x) {
    // Plexus configurator compatible setter that adds.
    this.define.add(x);
  }
  private final List<String> define = Lists.newArrayList();
  /**
   * Input and output charset for all files. By default, we accept
   * UTF-8 as input and output US_ASCII
   */
  public String charset;
  /**
   * Specifies the compilation level to use.
   * Options: WHITESPACE_ONLY, SIMPLE_OPTIMIZATIONS, ADVANCED_OPTIMIZATIONS
   */
  public com.google.javascript.jscomp.CompilationLevel compilationLevel;
  /** Don't generate output. Run checks, but no optimization passes. */
  public Boolean checksOnly;
  /** Enable or disable the optimizations based on available type
   * information. Inaccurate type annotations may result in incorrect
   * results. */
  public Boolean useTypesForOptimization;
  /**
   * Enable additional optimizations based on the assumption that the
   * output will be wrapped with a function wrapper.  This flag is
   * used to indicate that "global" declarations will not actually be
   * global but instead isolated to the compilation unit. This enables
   * additional optimizations.
   */
  public Boolean assumeFunctionWrapper;
  /** Specifies the warning level to use. Options: QUIET, DEFAULT, VERBOSE */
  public com.google.javascript.jscomp.WarningLevel warningLevel;
  /** Enable debugging options */
  public Boolean debug;
  /** Generates export code for those marked with @export */
  public Boolean generateExports;
  /** Generates export code for local properties marked with @export */
  public Boolean exportLocalPropertyDefinitions;
  /**
   * Specifies which formatting options, if any, should be applied to
   * the output JS.
   * Options: PRETTY_PRINT, PRINT_INPUT_DELIMITER, SINGLE_QUOTES
   */
  public void setFormatting(FormattingOption x) {
    // Plexus configurator compatible setter that adds.
    this.formatting.add(x);
  }
  private final List<FormattingOption> formatting = Lists.newArrayList();
  /** Process CommonJS modules to a concatenable form. */
  public Boolean processCommonJsModules;
  /** Path prefixes to be removed from ES6 and CommonJS modules. */
  public void setModuleRoot(String x) {
    // Plexus configurator compatible setter that adds.
    this.moduleRoot.add(x);
  }
  private final List<String> moduleRoot = Lists.newArrayList();
  /** Transform AMD to CommonJS modules. */
  public Boolean transformAmdModules;
  /** Processes built-ins from the Closure library, such as
   * goog.require(), goog.provide(), and goog.exportSymbol(). True by
   * default. */
  public Boolean processClosurePrimitives;
  /** Processes built-ins from the Jquery library, such as jQuery.fn
   * and jQuery.extend() */
  public Boolean processJqueryPrimitives;
  /** Generate $inject properties for AngularJS for functions
   * annotated with @ngInject */
  public Boolean angularPass;
  /** Rewrite Polymer classes to be compiler-friendly. */
  public Boolean polymerPass;
  /** Rewrite Dart Dev Compiler output to be compiler-friendly. */
  public Boolean dartPass;
  /**
   * Rewrite J2CL output to be compiler-friendly if enabled (ON or AUTO).
   * Options:OFF, ON, AUTO(default)
   */
  public CompilerOptions.J2clPassMode j2clPassMode;
  /** Prints out a list of all the files in the compilation. If
   * --dependency_mode=STRICT or LOOSE is specified, this will not
   * include files that got dropped because they were not
   * required. The %outname% placeholder expands to the JS output
   * file. If you're using modularization, using %outname% will create
   * a manifest for each module. */
  public String outputManifest;
  /** Prints out a JSON file of dependencies between modules. */
  public String outputModuleDependencies;
  /** Sets what language spec that input sources conform. Options:
   * ECMASCRIPT3, ECMASCRIPT5, ECMASCRIPT5_STRICT, ECMASCRIPT6
   * (default), ECMASCRIPT6_STRICT, ECMASCRIPT6_TYPED
   * (experimental) */
  public CompilerOptions.LanguageMode languageIn;
  /**
   * Sets what language spec the output should conform to. Options:
   * ECMASCRIPT3 (default), ECMASCRIPT5, ECMASCRIPT5_STRICT,
   * ECMASCRIPT6_TYPED (experimental)
   */
  public CompilerOptions.LanguageMode languageOut;
  /** Prints the compiler version to stdout and exit. */
  public Boolean version;
  /** Source of translated messages. Currently only supports XTB. */
  public String translationsFile;
  /**
   * Scopes all translations to the specified project.When specified,
   * we will use different message ids so that messages in different
   * projects can have different translations.
   */
  public String translationsProject;
  /** A file containing additional command-line options. */
  public String flagFile;
  /**
   * A file containing warnings to suppress. Each line should be of the
   * form {@code <file-name>:<line-number>?}  {@code <warning-description>}
   */
  public String warningsWhitelistFile;
  /** If specified, files whose path contains this string will have
   * their warnings hidden. You may specify multiple. */
  public void setHideWarningsFor(String x) {
    // Plexus configurator compatible setter that adds.
    this.hideWarningsFor.add(x);
  }
  private final List<String> hideWarningsFor = Lists.newArrayList();
  /** A whitelist of tag names in JSDoc. You may specify multiple */
  public void setExtraAnnotationName(String x) {
    // Plexus configurator compatible setter that adds.
    this.extraAnnotationName.add(x);
  }
  private final List<String> extraAnnotationName = Lists.newArrayList();
  /**
   * Shows the duration of each compiler pass and the impact to the
   * compiled output size.
   * Options: ALL, RAW_SIZE, TIMING_ONLY, OFF
   */
  public com.google.javascript.jscomp.CompilerOptions.TracerMode tracerMode;
  /** Checks for type errors using the new type inference algorithm. */
  public Boolean useNewTypeInference;
  /**
   * Specifies the name of an object that will be used to store all
   * non-extern globals
   */
  public String renamePrefixNamespace;
  /** A list of JS Conformance configurations in text protocol buffer format. */
  public void setConformanceConfig(String x) {
    // Plexus configurator compatible setter that adds.
    this.conformanceConfigs.add(x);
  }
  private final List<String> conformanceConfigs = Lists.newArrayList();
  /**
   * Determines the set of builtin externs to load. Options: BROWSER,
   * CUSTOM. Defaults to BROWSER.
   */
  public CompilerOptions.Environment environment;
  /** A file containing an instrumentation template. */
  public String instrumentationFile;
  /** Preserves type annotations. */
  public Boolean preserveTypeAnnotations;
  /** Allow injecting runtime libraries. */
  public Boolean injectLibraries;
  /**
   * Specifies how the compiler should determine the set and order of
   * files for a compilation.
   * <p>
   * Options: NONE the compiler will include
   * all src files in the order listed, STRICT files will be included
   * and sorted by starting from namespaces or files listed by the
   * --entry_point flag - files will only be included if they are
   * referenced by a goog.require or CommonJS require or ES6 import,
   * LOOSE same as with STRICT but files which do not goog.provide a
   * namespace and are not modules will be automatically added as
   * --entry_point entries. Defaults to NONE.
   */
  public DependencyMode dependencyMode;
  /**
   * A file or namespace to use as the starting point for determining
   * which src files to include in the compilation. ES6 and CommonJS
   * modules are specified as file paths (without the
   * extension). Closure-library namespaces are specified with a
   * "goog:" prefix. Example: --entry_point=goog:goog.Promise
   */
  public void setEntryPoint(String x) {
    // Plexus configurator compatible setter that adds.
    this.entryPoints.add(x);
  }
  private final List<String> entryPoints = Lists.newArrayList();
  /**
   * Rewrite ES6 library calls to use polyfills provided by the
   * compiler's runtime.
   */
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
   * I would prefer to use the latter programmaticly.
   */
  public void addArgv(
      @SuppressWarnings("unused") Log log,
      ImmutableList.Builder<String> argv) {
    for (Field f : JsOptions.class.getDeclaredFields()) {
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
    compilerOptions.setClosurePass(true);
    return compilerOptions;
  }

  private static void addFlag(
      String name, Field f, Object value, ImmutableList.Builder<String> argv) {
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
      Class<?> flagsClass;
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
  protected ImmutableList<FileExt> sourceExtensions() {
    return ImmutableList.of(FileExt.JS);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((angularPass == null) ? 0 : angularPass.hashCode());
    result = prime * result + ((assumeFunctionWrapper == null) ? 0 : assumeFunctionWrapper.hashCode());
    result = prime * result + ((charset == null) ? 0 : charset.hashCode());
    result = prime * result + ((checksOnly == null) ? 0 : checksOnly.hashCode());
    result = prime * result + ((compilationLevel == null) ? 0 : compilationLevel.hashCode());
    result = prime * result + ((conformanceConfigs == null) ? 0 : conformanceConfigs.hashCode());
    result = prime * result + ((dartPass == null) ? 0 : dartPass.hashCode());
    result = prime * result + ((debug == null) ? 0 : debug.hashCode());
    result = prime * result + ((define == null) ? 0 : define.hashCode());
    result = prime * result + ((dependencyMode == null) ? 0 : dependencyMode.hashCode());
    result = prime * result + ((entryPoints == null) ? 0 : entryPoints.hashCode());
    result = prime * result + ((environment == null) ? 0 : environment.hashCode());
    result = prime * result
        + ((exportLocalPropertyDefinitions == null) ? 0 : exportLocalPropertyDefinitions.hashCode());
    result = prime * result + ((extraAnnotationName == null) ? 0 : extraAnnotationName.hashCode());
    result = prime * result + ((flagFile == null) ? 0 : flagFile.hashCode());
    result = prime * result + ((formatting == null) ? 0 : formatting.hashCode());
    result = prime * result + ((generateExports == null) ? 0 : generateExports.hashCode());
    result = prime * result + ((hideWarningsFor == null) ? 0 : hideWarningsFor.hashCode());
    result = prime * result + ((injectLibraries == null) ? 0 : injectLibraries.hashCode());
    result = prime * result + ((instrumentationFile == null) ? 0 : instrumentationFile.hashCode());
    result = prime * result + ((j2clPassMode == null) ? 0 : j2clPassMode.hashCode());
    result = prime * result + ((jscompError == null) ? 0 : jscompError.hashCode());
    result = prime * result + ((jscompOff == null) ? 0 : jscompOff.hashCode());
    result = prime * result + ((jscompWarning == null) ? 0 : jscompWarning.hashCode());
    result = prime * result + ((languageIn == null) ? 0 : languageIn.hashCode());
    result = prime * result + ((languageOut == null) ? 0 : languageOut.hashCode());
    result = prime * result + ((moduleOutputPathPrefix == null) ? 0 : moduleOutputPathPrefix.hashCode());
    result = prime * result + ((moduleRoot == null) ? 0 : moduleRoot.hashCode());
    result = prime * result + ((moduleWrapper == null) ? 0 : moduleWrapper.hashCode());
    result = prime * result + ((outputManifest == null) ? 0 : outputManifest.hashCode());
    result = prime * result + ((outputModuleDependencies == null) ? 0 : outputModuleDependencies.hashCode());
    result = prime * result + ((outputWrapper == null) ? 0 : outputWrapper.hashCode());
    result = prime * result + ((outputWrapperFile == null) ? 0 : outputWrapperFile.hashCode());
    result = prime * result + ((polymerPass == null) ? 0 : polymerPass.hashCode());
    result = prime * result + ((preserveTypeAnnotations == null) ? 0 : preserveTypeAnnotations.hashCode());
    result = prime * result + ((printSourceAfterEachPass == null) ? 0 : printSourceAfterEachPass.hashCode());
    result = prime * result + ((processClosurePrimitives == null) ? 0 : processClosurePrimitives.hashCode());
    result = prime * result + ((processCommonJsModules == null) ? 0 : processCommonJsModules.hashCode());
    result = prime * result + ((processJqueryPrimitives == null) ? 0 : processJqueryPrimitives.hashCode());
    result = prime * result + ((renamePrefixNamespace == null) ? 0 : renamePrefixNamespace.hashCode());
    result = prime * result + ((rewritePolyfills == null) ? 0 : rewritePolyfills.hashCode());
    result = prime * result + ((sourceMapFormat == null) ? 0 : sourceMapFormat.hashCode());
    result = prime * result + ((sourceMapInputs == null) ? 0 : sourceMapInputs.hashCode());
    result = prime * result + ((sourceMapLocationMapping == null) ? 0 : sourceMapLocationMapping.hashCode());
    result = prime * result + ((summaryDetailLevel == null) ? 0 : summaryDetailLevel.hashCode());
    result = prime * result + ((thirdParty == null) ? 0 : thirdParty.hashCode());
    result = prime * result + ((tracerMode == null) ? 0 : tracerMode.hashCode());
    result = prime * result + ((transformAmdModules == null) ? 0 : transformAmdModules.hashCode());
    result = prime * result + ((translationsFile == null) ? 0 : translationsFile.hashCode());
    result = prime * result + ((translationsProject == null) ? 0 : translationsProject.hashCode());
    result = prime * result + ((useNewTypeInference == null) ? 0 : useNewTypeInference.hashCode());
    result = prime * result + ((useTypesForOptimization == null) ? 0 : useTypesForOptimization.hashCode());
    result = prime * result + ((version == null) ? 0 : version.hashCode());
    result = prime * result + ((warningLevel == null) ? 0 : warningLevel.hashCode());
    result = prime * result + ((warningsWhitelistFile == null) ? 0 : warningsWhitelistFile.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    JsOptions other = (JsOptions) obj;
    if (!super.equals(other)) { return false; }
    if (angularPass == null) {
      if (other.angularPass != null) {
        return false;
      }
    } else if (!angularPass.equals(other.angularPass)) {
      return false;
    }
    if (assumeFunctionWrapper == null) {
      if (other.assumeFunctionWrapper != null) {
        return false;
      }
    } else if (!assumeFunctionWrapper.equals(other.assumeFunctionWrapper)) {
      return false;
    }
    if (charset == null) {
      if (other.charset != null) {
        return false;
      }
    } else if (!charset.equals(other.charset)) {
      return false;
    }
    if (checksOnly == null) {
      if (other.checksOnly != null) {
        return false;
      }
    } else if (!checksOnly.equals(other.checksOnly)) {
      return false;
    }
    if (compilationLevel != other.compilationLevel) {
      return false;
    }
    if (conformanceConfigs == null) {
      if (other.conformanceConfigs != null) {
        return false;
      }
    } else if (!conformanceConfigs.equals(other.conformanceConfigs)) {
      return false;
    }
    if (dartPass == null) {
      if (other.dartPass != null) {
        return false;
      }
    } else if (!dartPass.equals(other.dartPass)) {
      return false;
    }
    if (debug == null) {
      if (other.debug != null) {
        return false;
      }
    } else if (!debug.equals(other.debug)) {
      return false;
    }
    if (define == null) {
      if (other.define != null) {
        return false;
      }
    } else if (!define.equals(other.define)) {
      return false;
    }
    if (dependencyMode != other.dependencyMode) {
      return false;
    }
    if (entryPoints == null) {
      if (other.entryPoints != null) {
        return false;
      }
    } else if (!entryPoints.equals(other.entryPoints)) {
      return false;
    }
    if (environment != other.environment) {
      return false;
    }
    if (exportLocalPropertyDefinitions == null) {
      if (other.exportLocalPropertyDefinitions != null) {
        return false;
      }
    } else if (!exportLocalPropertyDefinitions.equals(other.exportLocalPropertyDefinitions)) {
      return false;
    }
    if (extraAnnotationName == null) {
      if (other.extraAnnotationName != null) {
        return false;
      }
    } else if (!extraAnnotationName.equals(other.extraAnnotationName)) {
      return false;
    }
    if (flagFile == null) {
      if (other.flagFile != null) {
        return false;
      }
    } else if (!flagFile.equals(other.flagFile)) {
      return false;
    }
    if (formatting == null) {
      if (other.formatting != null) {
        return false;
      }
    } else if (!formatting.equals(other.formatting)) {
      return false;
    }
    if (generateExports == null) {
      if (other.generateExports != null) {
        return false;
      }
    } else if (!generateExports.equals(other.generateExports)) {
      return false;
    }
    if (hideWarningsFor == null) {
      if (other.hideWarningsFor != null) {
        return false;
      }
    } else if (!hideWarningsFor.equals(other.hideWarningsFor)) {
      return false;
    }
    if (injectLibraries == null) {
      if (other.injectLibraries != null) {
        return false;
      }
    } else if (!injectLibraries.equals(other.injectLibraries)) {
      return false;
    }
    if (instrumentationFile == null) {
      if (other.instrumentationFile != null) {
        return false;
      }
    } else if (!instrumentationFile.equals(other.instrumentationFile)) {
      return false;
    }
    if (j2clPassMode != other.j2clPassMode) {
      return false;
    }
    if (jscompError == null) {
      if (other.jscompError != null) {
        return false;
      }
    } else if (!jscompError.equals(other.jscompError)) {
      return false;
    }
    if (jscompOff == null) {
      if (other.jscompOff != null) {
        return false;
      }
    } else if (!jscompOff.equals(other.jscompOff)) {
      return false;
    }
    if (jscompWarning == null) {
      if (other.jscompWarning != null) {
        return false;
      }
    } else if (!jscompWarning.equals(other.jscompWarning)) {
      return false;
    }
    if (languageIn != other.languageIn) {
      return false;
    }
    if (languageOut != other.languageOut) {
      return false;
    }
    if (moduleOutputPathPrefix == null) {
      if (other.moduleOutputPathPrefix != null) {
        return false;
      }
    } else if (!moduleOutputPathPrefix.equals(other.moduleOutputPathPrefix)) {
      return false;
    }
    if (moduleRoot == null) {
      if (other.moduleRoot != null) {
        return false;
      }
    } else if (!moduleRoot.equals(other.moduleRoot)) {
      return false;
    }
    if (moduleWrapper == null) {
      if (other.moduleWrapper != null) {
        return false;
      }
    } else if (!moduleWrapper.equals(other.moduleWrapper)) {
      return false;
    }
    if (outputManifest == null) {
      if (other.outputManifest != null) {
        return false;
      }
    } else if (!outputManifest.equals(other.outputManifest)) {
      return false;
    }
    if (outputModuleDependencies == null) {
      if (other.outputModuleDependencies != null) {
        return false;
      }
    } else if (!outputModuleDependencies.equals(other.outputModuleDependencies)) {
      return false;
    }
    if (outputWrapper == null) {
      if (other.outputWrapper != null) {
        return false;
      }
    } else if (!outputWrapper.equals(other.outputWrapper)) {
      return false;
    }
    if (outputWrapperFile == null) {
      if (other.outputWrapperFile != null) {
        return false;
      }
    } else if (!outputWrapperFile.equals(other.outputWrapperFile)) {
      return false;
    }
    if (polymerPass == null) {
      if (other.polymerPass != null) {
        return false;
      }
    } else if (!polymerPass.equals(other.polymerPass)) {
      return false;
    }
    if (preserveTypeAnnotations == null) {
      if (other.preserveTypeAnnotations != null) {
        return false;
      }
    } else if (!preserveTypeAnnotations.equals(other.preserveTypeAnnotations)) {
      return false;
    }
    if (printSourceAfterEachPass == null) {
      if (other.printSourceAfterEachPass != null) {
        return false;
      }
    } else if (!printSourceAfterEachPass.equals(other.printSourceAfterEachPass)) {
      return false;
    }
    if (processClosurePrimitives == null) {
      if (other.processClosurePrimitives != null) {
        return false;
      }
    } else if (!processClosurePrimitives.equals(other.processClosurePrimitives)) {
      return false;
    }
    if (processCommonJsModules == null) {
      if (other.processCommonJsModules != null) {
        return false;
      }
    } else if (!processCommonJsModules.equals(other.processCommonJsModules)) {
      return false;
    }
    if (processJqueryPrimitives == null) {
      if (other.processJqueryPrimitives != null) {
        return false;
      }
    } else if (!processJqueryPrimitives.equals(other.processJqueryPrimitives)) {
      return false;
    }
    if (renamePrefixNamespace == null) {
      if (other.renamePrefixNamespace != null) {
        return false;
      }
    } else if (!renamePrefixNamespace.equals(other.renamePrefixNamespace)) {
      return false;
    }
    if (rewritePolyfills == null) {
      if (other.rewritePolyfills != null) {
        return false;
      }
    } else if (!rewritePolyfills.equals(other.rewritePolyfills)) {
      return false;
    }
    if (sourceMapFormat != other.sourceMapFormat) {
      return false;
    }
    if (sourceMapInputs == null) {
      if (other.sourceMapInputs != null) {
        return false;
      }
    } else if (!sourceMapInputs.equals(other.sourceMapInputs)) {
      return false;
    }
    if (sourceMapLocationMapping == null) {
      if (other.sourceMapLocationMapping != null) {
        return false;
      }
    } else if (!sourceMapLocationMapping.equals(other.sourceMapLocationMapping)) {
      return false;
    }
    if (summaryDetailLevel == null) {
      if (other.summaryDetailLevel != null) {
        return false;
      }
    } else if (!summaryDetailLevel.equals(other.summaryDetailLevel)) {
      return false;
    }
    if (thirdParty == null) {
      if (other.thirdParty != null) {
        return false;
      }
    } else if (!thirdParty.equals(other.thirdParty)) {
      return false;
    }
    if (tracerMode != other.tracerMode) {
      return false;
    }
    if (transformAmdModules == null) {
      if (other.transformAmdModules != null) {
        return false;
      }
    } else if (!transformAmdModules.equals(other.transformAmdModules)) {
      return false;
    }
    if (translationsFile == null) {
      if (other.translationsFile != null) {
        return false;
      }
    } else if (!translationsFile.equals(other.translationsFile)) {
      return false;
    }
    if (translationsProject == null) {
      if (other.translationsProject != null) {
        return false;
      }
    } else if (!translationsProject.equals(other.translationsProject)) {
      return false;
    }
    if (useNewTypeInference == null) {
      if (other.useNewTypeInference != null) {
        return false;
      }
    } else if (!useNewTypeInference.equals(other.useNewTypeInference)) {
      return false;
    }
    if (useTypesForOptimization == null) {
      if (other.useTypesForOptimization != null) {
        return false;
      }
    } else if (!useTypesForOptimization.equals(other.useTypesForOptimization)) {
      return false;
    }
    if (version == null) {
      if (other.version != null) {
        return false;
      }
    } else if (!version.equals(other.version)) {
      return false;
    }
    if (warningLevel != other.warningLevel) {
      return false;
    }
    if (warningsWhitelistFile == null) {
      if (other.warningsWhitelistFile != null) {
        return false;
      }
    } else if (!warningsWhitelistFile.equals(other.warningsWhitelistFile)) {
      return false;
    }
    return true;
  }
}
