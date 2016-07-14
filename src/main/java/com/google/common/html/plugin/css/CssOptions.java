package com.google.common.html.plugin.css;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Ascii;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.css.GssFunctionMapProvider;
import com.google.common.css.JobDescription;
import com.google.common.css.JobDescriptionBuilder;
import com.google.common.css.OutputRenamingMapFormat;
import com.google.common.css.SourceCode;
import com.google.common.css.SubstitutionMapProvider;
import com.google.common.css.Vendor;
import com.google.common.css.JobDescription.OptimizeStrategy;
import com.google.common.html.plugin.Options;
import com.google.common.html.plugin.OptionsUtils;
import com.google.common.html.plugin.OutputAmbiguityChecker;
import com.google.common.html.plugin.Sources;
import com.google.common.io.Files;

/**
 * Options for processing
 * <a href="https://github.com/google/closure-stylesheets">Closure Stylesheets</a>.
 */
public final class CssOptions implements Options {

  private static final long serialVersionUID = 8205371531045169081L;

  public String id;

  /** Allows @defs and @mixins from one file to propagate to other files. */
  public Boolean allowDefPropagation;
  /** Specify a non-standard function to whitelist, like alpha() */
  public String[] allowedNonStandardFunctions;
  public String[] allowedUnrecognizedProperties;
  public Boolean allowKeyframes;
  public Boolean allowMozDocument;
  public Boolean allowUndefinedConstants;
  public Boolean allowUnrecognizedFunctions;
  public Boolean allowUnrecognizedProperties;
  public Boolean allowWebkitKeyframes;
  /**
   * JSON map from strings to integers that specify integer constants
   * to be used in for loops.
   */
  public String compileConstants;
  /** Copyright notice to prepend to the output. */
  public String copyrightNotice;
  /** Add a prefix to all renamed css class names. */
  public String cssRenamingPrefix;
  public Boolean eliminateDeadStyles;
  /** A list of CSS class names that shoudn't be renamed. */
  public String[] excludedClassesFromRenaming;
  /**
   * The fully qualified class name of a map provider of custom
   * GSS functions to resolve.
   */
  public Class<? extends GssFunctionMapProvider> gssFunctionMapProvider;
  /**
   * This specifies the display orientation the input files were
   * written for. You can choose between: LTR, RTL. LTR is the default
   * and means that the input style sheets were designed for use with
   * left to right display User Agents. RTL sheets are designed for
   * use with right to left UAs. Currently, all input files must have
   * the same orientation, as there is no way to specify the
   * orientation on a per-file or per-library basis.
   */
  public JobDescription.InputOrientation inputOrientation;
  public JobDescription.OptimizeStrategy optimize;
  /**
   * Whether to format the output with newlines and indents so that
   * it is more readable.
   */
  public JobDescription.OutputFormat outputFormat;
  /**
   * Specify this option to perform automatic right to left conversion
   * of the input. You can choose between: LTR, RTL,
   * NOCHANGE. NOCHANGE means the input will not be changed in any way
   * with respect to direction issues. LTR outputs a sheet suitable
   * for left to right display and RTL outputs a sheet suitable for
   * right to left display. If the input orientation is different than
   * the requested output orientation, 'left' and 'right' values in
   * direction sensitive style rules are flipped. If the input already
   * has the desired orientation, this option effectively does nothing
   * except for defining GSS_LTR and GSS_RTL, respectively. The input
   * is LTR by default and can be changed with the input_orientation
   * flag.
   * <p>
   * When specified multiple times, multiple outputs are specified and
   * the {orient} can be used in the output path template to put output
   * compiled with different orientations in different output files.
   */
  public JobDescription.OutputOrientation[] outputOrientation;
  /** How to format the output from the CSS class renaming. */
  public OutputRenamingMapFormat outputRenamingMapFormat;
  /** Preserve comments from sources into pretty printed output css. */
  public Boolean preserveComments;
  public Boolean processDependencies;
  public Boolean simplifyCss;
  /**
   * The level to generate source maps. You could choose between
   * DEFAULT, which will generate source map only for selectors,
   * blocks, rules, variables and symbol mappings, and ALL, which
   * outputs mappings for all elements.
   */
  public JobDescription.SourceMapDetailLevel sourceMapLevel;
  public Boolean suppressDependencyCheck;
  public Boolean swapLeftRightInUrl;
  public Boolean swapLtrRtlInUrl;
  /**
   * Specifies the name of a true condition.
   * The condition name can be used in @if boolean expressions.  The
   * conditions are ignored if GSS extensions are not enabled.
   */
  public String[] trueConditionNames;
  public Boolean useInternalBidiFlipper;
  /**
   * Creates browser-vendor-specific output by stripping all
   * proprietary browser-vendor properties from the output except for
   * those associated with this vendor.
   */
  public Vendor[] vendor;

  public File[] source;
  /**
   * The output CSS filename. If empty, standard output will be
   * used. The output is always UTF-8 encoded.
   * Defaults to target/css/{reldir}/compiled{-basename}{-orient}.css
   */
  public String output;
  /**
   * The source map output.
   * Provides a mapping from the generated output to their original
   * source code location.
   * Defaults to target/css/{reldir}/source-map{-basename}{-orient}.json
   */
  public String sourceMapFile;

  public String getKey() {
    return id != null ? "css-options:" + id : null;
  }

  public String getId() {
    return id;
  }

  JobDescription getJobDescription(
      Log log, Iterable<? extends Sources.Source> sources,
      SubstitutionMapProvider cssSubstitutionMapProvider)
  throws IOException {
    JobDescriptionBuilder jobDescriptionBuilder = new JobDescriptionBuilder();

    jobDescriptionBuilder.setOptimizeStrategy(OptimizeStrategy.SAFE);

    for (Sources.Source src : sources) {
      String fileContent;
      try {
        fileContent = Files.toString(src.canonicalPath, Charsets.UTF_8);
      } catch (IOException ex) {
        log.error("Failed to read " + src.canonicalPath);
        throw ex;
      }
      jobDescriptionBuilder.addInput(new SourceCode(
          src.relativePath.getPath(), fileContent));
    }

    if (wasSet(allowDefPropagation)) {
      jobDescriptionBuilder.setAllowDefPropagation(allowDefPropagation);
    }
    if (wasSet(allowedNonStandardFunctions)) {
      jobDescriptionBuilder.setAllowedNonStandardFunctions(
          ImmutableList.copyOf(allowedNonStandardFunctions));
    }
    if (wasSet(allowedUnrecognizedProperties)) {
      jobDescriptionBuilder.setAllowedUnrecognizedProperties(
          ImmutableList.copyOf(allowedUnrecognizedProperties));
    }
    if (wasSet(allowKeyframes)) {
      jobDescriptionBuilder.setAllowKeyframes(allowKeyframes);
    }
    if (wasSet(allowMozDocument)) {
      jobDescriptionBuilder.setAllowMozDocument(allowMozDocument);
    }
    if (wasSet(allowUndefinedConstants)) {
      jobDescriptionBuilder.setAllowUndefinedConstants(allowUndefinedConstants);
    }
    if (wasSet(allowUnrecognizedFunctions)) {
      jobDescriptionBuilder.setAllowUnrecognizedFunctions(
          allowUnrecognizedFunctions);
    }
    if (wasSet(allowUnrecognizedProperties)) {
      jobDescriptionBuilder.setAllowUnrecognizedProperties(
          allowUnrecognizedProperties);
    }
    if (wasSet(allowWebkitKeyframes) && allowWebkitKeyframes.booleanValue()) {
      jobDescriptionBuilder.allowWebkitKeyframes();
    }
    if (wasSet(compileConstants)) {
      Optional<ImmutableMap<String, Object>> constants =
          OptionsUtils.keyValueMapFromJson(log, compileConstants);
      if (constants.isPresent()) {
        Optional<ImmutableMap<String, Integer>> constantsTyped =
            requireValuesHaveType(log, constants.get(), Integer.class, "compileConstants");
        if (constantsTyped.isPresent()) {
          jobDescriptionBuilder.setCompileConstants(constantsTyped.get());
        }
      }
    }
    if (wasSet(copyrightNotice)) {
      jobDescriptionBuilder.setCopyrightNotice(copyrightNotice);
    }
    if (wasSet(cssRenamingPrefix)) {
      jobDescriptionBuilder.setCssRenamingPrefix(cssRenamingPrefix);
    }
    jobDescriptionBuilder.setCssSubstitutionMapProvider(
        cssSubstitutionMapProvider);
    if (wasSet(eliminateDeadStyles)) {
      jobDescriptionBuilder.setEliminateDeadStyles(eliminateDeadStyles);
    }
    if (wasSet(excludedClassesFromRenaming)) {
      jobDescriptionBuilder.setExcludedClassesFromRenaming(
          ImmutableList.copyOf(excludedClassesFromRenaming));
    }
    if (wasSet(gssFunctionMapProvider)) {
      Optional<GssFunctionMapProvider> provider =
          OptionsUtils.createInstanceUsingDefaultConstructor(
              log, GssFunctionMapProvider.class, gssFunctionMapProvider);
      if (provider.isPresent()) {
        jobDescriptionBuilder.setGssFunctionMapProvider(provider.get());
      }
    }
    if (wasSet(inputOrientation)) {
      jobDescriptionBuilder.setInputOrientation(inputOrientation);
    }
    if (wasSet(optimize)) {
      jobDescriptionBuilder.setOptimizeStrategy(optimize);
    }
    if (wasSet(outputFormat)) {
      jobDescriptionBuilder.setOutputFormat(outputFormat);
    }
    if (outputOrientation != null && outputOrientation.length == 1) {
      jobDescriptionBuilder.setOutputOrientation(outputOrientation[0]);
    }
    jobDescriptionBuilder.setOutputRenamingMapFormat(
        OutputRenamingMapFormat.JSON);
    if (wasSet(preserveComments)) {
      jobDescriptionBuilder.setPreserveComments(preserveComments);
    }
    if (wasSet(processDependencies)) {
      jobDescriptionBuilder.setProcessDependencies(processDependencies);
    }
    if (wasSet(simplifyCss)) {
      jobDescriptionBuilder.setSimplifyCss(simplifyCss);
    }
    if (wasSet(sourceMapLevel)) {
      jobDescriptionBuilder.setSourceMapLevel(sourceMapLevel);
    }
    if (wasSet(suppressDependencyCheck)) {
      jobDescriptionBuilder.setSuppressDependencyCheck(suppressDependencyCheck);
    }
    if (wasSet(swapLeftRightInUrl)) {
      jobDescriptionBuilder.setSwapLeftRightInUrl(swapLeftRightInUrl);
    }
    if (wasSet(swapLtrRtlInUrl)) {
      jobDescriptionBuilder.setSwapLtrRtlInUrl(swapLtrRtlInUrl);
    }
    if (wasSet(trueConditionNames)) {
      jobDescriptionBuilder.setTrueConditionNames(
          ImmutableList.copyOf(trueConditionNames));
    }
    if (wasSet(useInternalBidiFlipper)) {
      jobDescriptionBuilder.setUseInternalBidiFlipper(useInternalBidiFlipper);
    }
    if (vendor != null && vendor.length == 1) {
      jobDescriptionBuilder.setVendor(vendor[0]);
    }

    jobDescriptionBuilder.setCreateSourceMap(true);

    return jobDescriptionBuilder.getJobDescription();
  }


  static <K, T>
  Optional<ImmutableMap<K, T>> requireValuesHaveType(
      Log log, ImmutableMap<K, ?> m, Class<T> valueType, String desc) {
    for (Map.Entry<K, ?> e : m.entrySet()) {
      Object v = e.getValue();
      if (v != null && !valueType.isInstance(v)) {
        log.error("Value " + v + " in " + desc
            + " has type " + v.getClass().getSimpleName()
            + ", not " + valueType.getSimpleName());
        return Optional.absent();
      }
    }
    @SuppressWarnings("unchecked")
    ImmutableMap<K, T> typedMap = (ImmutableMap<K, T>) m;
    return Optional.of(typedMap);
  }

  public static final class Outputs implements Serializable {
    private static final long serialVersionUID = -600433593903008098L;

    public final File css;
    public final File sourceMap;

    public Outputs(
        CssOptions opts,
        Sources.Source source,
        String defaultCssOutputPathTemplate,
        String defaultCssSourceMapPathTemplate) {

      String basename = FilenameUtils.removeExtension(
          source.relativePath.getName());
      String reldir = source.relativePath.getParent();
      String orientation =
          opts.outputOrientation != null && opts.outputOrientation.length == 1
          ? Ascii.toLowerCase(opts.outputOrientation[0].name()) : null;
      String vendor =
          opts.vendor != null && opts.vendor.length == 1
          ? Ascii.toLowerCase(opts.vendor[0].name()) : null;
      PathTemplateSubstitutor ts;
      {
        ImmutableMap.Builder<String, String> b = ImmutableMap.builder();
        if (basename != null) { b.put("basename", basename); }
        if (reldir != null) { b.put("reldir", reldir); }
        if (orientation != null) { b.put("orientation", orientation); }
        if (vendor != null) { b.put("vendor", vendor); }
        ts = new PathTemplateSubstitutor(b.build());
      }
      this.css = ts.substitute(
          Optional.fromNullable(opts.output)
          .or(defaultCssOutputPathTemplate));
      this.sourceMap = ts.substitute(
          Optional.fromNullable(opts.sourceMapFile)
          .or(defaultCssSourceMapPathTemplate));
    }

    public ImmutableList<File> allOutputFiles() {
      return ImmutableList.of(css, sourceMap);
    }

    public ImmutableList<OutputAmbiguityChecker.Output> allOutputs() {
      return ImmutableList.of(
          new OutputAmbiguityChecker.Output("Compiled CSS", css),
          new OutputAmbiguityChecker.Output("CSS Source Map", sourceMap));

    }
  }

  /**
   * One CssOptions instance with single field values for any fields that can
   * be substituted into an output path template for each combination of such
   * field values.
   */
  static ImmutableList<CssOptions> asplode(CssOptions[] csses) {
    ImmutableList<CssOptions> csses0 = csses == null || csses.length == 0
        ? ImmutableList.of(new CssOptions()) : ImmutableList.copyOf(csses);
    ImmutableList<CssOptions> csses1 = asplodeOutputOrientation(csses0);
    ImmutableList<CssOptions> csses2 = asplodeVendor(csses1);
    return csses2;
  }

  @Override
  public CssOptions clone() {
    try {
      return (CssOptions) super.clone();
    } catch (CloneNotSupportedException ex) {
      throw (AssertionError) new AssertionError().initCause(ex);
    }
  }

  private static ImmutableList<CssOptions> asplodeOutputOrientation(
      ImmutableList<CssOptions> opts) {
    ImmutableList.Builder<CssOptions> asploded = ImmutableList.builder();
    for (CssOptions css : opts) {
      if (css.outputOrientation == null || css.outputOrientation.length <= 1) {
        asploded.add(css);
      } else {
        for (JobDescription.OutputOrientation oo
             : ImmutableSet.copyOf(css.outputOrientation)) {
          CssOptions clone = css.clone();
          clone.outputOrientation = new JobDescription.OutputOrientation[] {
            oo,
          };
          asploded.add(clone);
        }
      }
    }
    return asploded.build();
  }

  private static ImmutableList<CssOptions> asplodeVendor(
      ImmutableList<CssOptions> opts) {
    ImmutableList.Builder<CssOptions> asploded = ImmutableList.builder();
    for (CssOptions css : opts) {
      if (css.vendor == null || css.vendor.length <= 1) {
        asploded.add(css);
      } else {
        for (Vendor v : ImmutableSet.copyOf(css.vendor)) {
          CssOptions clone = css.clone();
          clone.vendor = new Vendor[] { v };
          asploded.add(clone);
        }
      }
    }
    return asploded.build();
  }

  private static boolean wasSet(String parameterValue) {
    return parameterValue != null;
  }

  private static boolean wasSet(Class<?> parameterValue) {
    return parameterValue != null;
  }

  private static boolean wasSet(String[] parameterValues) {
    return parameterValues != null && parameterValues.length != 0;
  }

  private static boolean wasSet(Boolean parameterValue) {
    return parameterValue != null;
  }

  private static boolean wasSet(Enum<?> parameterValue) {
    return parameterValue != null;
  }
}

final class PathTemplateSubstitutor {
  ImmutableMap<String, String> substitutions;
  PathTemplateSubstitutor(Map<? extends String, ? extends String> substitutions) {
    this.substitutions = ImmutableMap.<String, String>copyOf(substitutions);
  }

  private static final Pattern INTERPOLATION =
      Pattern.compile("[{]([^\\w{}]*)(\\w+)([^\\w{}]*)[}]");

  File substitute(String templateText) {
    Matcher m = INTERPOLATION.matcher(templateText);
    StringBuilder sb = new StringBuilder(templateText.length() * 2);
    int written = 0;
    while (m.find()) {
      int start = m.start();
      sb.append(templateText, written, start);
      written = m.end();
      String prefix = m.group(1);
      String key = m.group(2);
      String suffix = m.group(3);
      if (substitutions.containsKey(key)) {
        sb.append(prefix)
           .append(substitutions.get(key))
           .append(suffix);
      }
    }
    sb.append(templateText, written, templateText.length());
    return new File(sb.toString());
  }
}
