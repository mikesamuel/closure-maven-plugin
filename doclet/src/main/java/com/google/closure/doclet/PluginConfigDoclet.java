package com.google.closure.doclet;

import com.google.closure.doclet.Doclet.Element;
import com.google.closure.doclet.Doclet.Parameter;
import com.google.closure.module.ClosureModule;
import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.html.types.SafeHtmlProto;
import com.google.common.html.types.SafeHtmls;
import com.google.common.io.CharSink;
import com.google.common.io.Files;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.jbcsrc.api.Precompiled;
import com.google.template.soy.jbcsrc.api.SoySauce;
import com.google.template.soy.jbcsrc.api.SoySauce.Continuation;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.AnnotationDesc.ElementValuePair;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.Doc;
import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.LanguageVersion;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.SourcePosition;
import com.sun.javadoc.Type;
import com.sun.tools.doclets.standard.Standard;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.htmltypes.SafeHtmlMint;

/**
 * A doclet that renders configuration documentation for classes that are
 * provisioned by the plexus configurator.
 */
public final class PluginConfigDoclet {
  final RootDoc root;
  final Optional<ClassDoc> mojoInterface;
  private final List<String> startingClasses = Lists.newArrayList();
  private boolean okSoFar = true;
  private boolean verbose = false;
  private Optional<String> outdir = Optional.absent();

  /** If the */
  private static final boolean DEBUG = false;
  private PrintWriter log;

  PluginConfigDoclet(RootDoc root) {
    this.root = root;
    this.mojoInterface = Optional.fromNullable(
        root.classNamed(MOJO_INTERFACE_NAME));
  }

  void error(Doc d, String s) {
    SourcePosition sp = d != null ? d.position() : null;
    if (sp != null) {
      root.printError(sp, s);
    } else {
      root.printError(s);
    }
    okSoFar = false;
  }

  void info(Doc d, String s) {
    if (verbose) {
      SourcePosition sp = d != null ? d.position() : null;
      if (log != null) {
        log.println((sp != null ? sp.toString() + " : " : "") + s);
        log.flush();
      }
      if (sp != null) {
        root.printNotice(sp, s);
      } else {
        root.printNotice(s);
      }
    }
  }

  void notice(Doc d, String s) {
    SourcePosition sp = d != null ? d.position() : null;
    if (log != null) {
      log.println((sp != null ? sp.toString() + " : " : "") + s);
      log.flush();
    }
    if (sp != null) {
      root.printNotice(sp, s);
    } else {
      root.printNotice(s);
    }
  }

  /**
   * Name of an option to the doclet.
   */
  public enum OptionName {
    /**
     * A class to start looking at.  If not specified, we will look for all
     * non-abstract classes that implement {@code org.apache.maven.plugin.Mojo}.
     */
    STARTING_CLASS("-startingClass", 2) {
      @SuppressWarnings("synthetic-access")
      @Override
      boolean configure(
          PluginConfigDoclet doclet, ImmutableList<String> optionValues) {
        boolean ok = true;
        for (String className : optionValues) {
          if (doclet.root.classNamed(className) != null) {
            doclet.startingClasses.addAll(optionValues);
          } else {
            doclet.root.printError(
                "Option " + optionName + " has value "
                + className + " but there is no such class");
            ok = false;
          }
        }
        return ok;
      }
    },
    /**
     * True to turn on verbose logging.
     */
    VERBOSE("-verbose", 2) {
      @SuppressWarnings("synthetic-access")
      @Override
      boolean configure(
          PluginConfigDoclet doclet, ImmutableList<String> optionValues) {
        doclet.verbose = !optionValues.equals(ImmutableList.of("false"));
        return true;
      }
    },
    /**
     * Output directory.
     */
    OUTPUT_DIR("-d", 2) {
      @SuppressWarnings("synthetic-access")
      @Override
      boolean configure(
          PluginConfigDoclet doclet, ImmutableList<String> optionValues) {
        doclet.outdir = Optional.of(optionValues.get(0));
        return true;
      }
    },
    ;

    final String optionName;
    final int length;

    OptionName(String optionName, int length) {
      this.optionName = optionName;
      this.length = length;
    }

    @SuppressWarnings("static-method")
    boolean isValid(
        @SuppressWarnings("unused") String[] nameThenValues,
        @SuppressWarnings("unused") DocErrorReporter reporter) {
      return true;
    }

    abstract boolean configure(
        PluginConfigDoclet doclet, ImmutableList<String> optionValues);
  }

  static final ImmutableMap<String, OptionName> OPTION_NAMES;
  static {
    ImmutableMap.Builder<String, OptionName> b = ImmutableMap.builder();
    for (OptionName on : OptionName.values()) {
      b.put(on.optionName, on);
    }
    OPTION_NAMES = b.build();
  }


  // https://docs.oracle.com/javase/8/docs/jdk/api/javadoc/doclet/com/sun/javadoc/Doclet.html
  // Explains the doclet API which uses the static methods below.

  /**
   * Option check, forwards options to the standard doclet,
   * if that one refuses them, they are used by this doclet.
   */
  public static int optionLength(String option) {
    // Workaround http://stackoverflow.com/questions/19181236 courtesy
    // UmlGraphDoc.
    int result = Standard.optionLength(option);
    if (result != 0) {
      return result;
    } else {
      // Ones we want.
      if (OPTION_NAMES.containsKey(option)) { return 2; }
      return 0;
    }
  }

  /** Specified for the Doclet API */
  public static boolean validOptions(
      String[][] options,
      DocErrorReporter reporter) {
    ImmutableList.Builder<String[]> optionsForStandardDoclet
        = ImmutableList.builder();
    boolean ok = true;
    for (String[] optionArr : options) {
      OptionName on = OPTION_NAMES.get(optionArr[0]);
      if (on == null) {
        optionsForStandardDoclet.add(optionArr);
      } else {
        ok &= on.isValid(optionArr, reporter);
      }
    }
    ok &= Standard.validOptions(
        optionsForStandardDoclet.build().toArray(new String[0][]), reporter);
    return ok;
  }

  /** Specified for the Doclet API */
  public static boolean start(RootDoc root) {
    PluginConfigDoclet doclet = new PluginConfigDoclet(root);
    boolean ok = true;
    for (String[] optionArr : root.options()) {
      String name = optionArr[0];
      OptionName on = OPTION_NAMES.get(name);
      if (on != null) {
        ImmutableList<String> values = ImmutableList.copyOf(
            Arrays.asList(optionArr).subList(1, optionArr.length));
        ok &= on.configure(doclet, values);
      }
    }

    doclet.run();
    ok &= doclet.okSoFar;

    return ok;
  }

  /** Specified for the Doclet API */
  public static LanguageVersion languageVersion() {
    return LanguageVersion.JAVA_1_5;
  }

  static boolean isSelfExplanatory(Type t) {
    if (t.isPrimitive()) { return false; }
    String qualName = t.qualifiedTypeName();
    if (qualName.startsWith("java.lang.")) {
      return true;
    }
    if (File.class.getName().equals(qualName)) {
      return true;
    }
    return false;
  }


  void run() {
    Runnable runnable = new Runnable() {
      @SuppressWarnings("synthetic-access")
      @Override
      public void run() {
        ImmutableSet.Builder<String> entryPoints = ImmutableSet.builder();
        if (startingClasses.isEmpty()) {
          findMojos(entryPoints);
        } else {
          entryPoints.addAll(startingClasses);
        }

        Crawl crawl = new Crawl();

        crawl.crawlAll(entryPoints.build());

        if (!outdir.isPresent()) {
          error(root, "No output directory specified");
          return;
        }
        File outdirFile = new File(outdir.get());
        outdirFile.mkdirs();
        notice(
            root,
            "Writing " + crawl.configurables.values().size()
            + " to " + outdirFile);

        Renderer renderer = new Renderer();
        for (Configurable c : crawl.configurables.values()) {
          try {
            renderer.renderDocsFor(crawl, c, outdirFile);
          } catch (IOException ex) {
            if (DEBUG) {
              ex.printStackTrace();
            }
            error(null, ex.toString());
          }
        }
      }
    };

    if (DEBUG) {
      try (OutputStream logs = new FileOutputStream("/tmp/log")) {
        try (Writer logw = new OutputStreamWriter(logs, Charsets.UTF_8)) {
          try (PrintWriter logpw = new PrintWriter(logw)) {
            this.log = logpw;
            runnable.run();
          } finally {
            this.log = null;
          }
        }
      } catch (IOException ex) {
        Throwables.propagate(ex);
      }
    } else {
      runnable.run();
    }
  }


  final class Renderer {
    private final Injector injector;

    @Inject
    SoyValueHelper valueHelper;

    @Inject
    SoyCssRenamingMap cssRenamingMap;

    @Inject
    @Precompiled
    SoySauce soySauce;

    {
      injector = Guice.createInjector(new ClosureModule());
      injector.injectMembers(this);
    }

    String urlFor(Configurable c) {
      return "configuration-" + c.configurableClass.qualifiedName() + ".html";
    }

    void renderDocsFor(Crawl crawl, Configurable c, File outDir)
    throws IOException {
      Element e;
      {
        Element.Builder eb = Element.newBuilder();
        eb.setClassName(c.configurableClass.qualifiedName());
        String commentText = c.configurableClass.commentText();
        if (!Strings.isNullOrEmpty(commentText)) {
          eb.setCommentHtml(commentToHtml(commentText));
        }
        eb.setIsEnum(c.configurableClass.isEnum());
        eb.setIsMojo(c.isMojo);
        eb.addAllTagName(c.tagNames);
        // Present in lexicographic order.
        for (String paramName : Sets.newTreeSet(c.params.keySet())) {
          ParameterInfo pi = c.params.get(paramName);
          Parameter.Builder pb = Parameter.newBuilder();
          pb.setName(pi.name);
          if (pi.field.isPresent()) {
            FieldDoc fd = pi.field.get();
            pb.setField(fd.containingClass().qualifiedName() + "#" + fd.name());

          }
          if (pi.setter.isPresent()) {
            MethodDoc s = pi.setter.get();
            pb.setMethod(
                s.containingClass().qualifiedName()
                + "#" + s.name() + s.flatSignature());
          }
          pb.setSourcePosition(pi.source().position().toString());
          Type t = pi.getType();
          pb.setType(t.simpleTypeName());
          Optional<Configurable> tc = crawl.foundConfigurable(pi.source(), t);
          if (tc.isPresent()) {
            pb.setTypeUrl(urlFor(tc.get()));
          }
          String pCommentText = pi.getCommentText();
          if (!Strings.isNullOrEmpty(pCommentText)) {
            pb.setCommentHtml(commentToHtml(pCommentText));
          }
          eb.addParam(pb);
        }
        e = eb.build();
      }


      ImmutableMap<String, Object> data =
          ImmutableMap.<String, Object>of("e", e);
      SoySauce.Renderer renderer = soySauce
          .renderTemplate("com.google.closure.doclet.soy.Configurable")
          .setData(data)
          ;

      Continuation<String> renderedHtml = renderer.render();
      while (!renderedHtml.result().isDone()) {
        renderedHtml.continueRender();
      }

      File outFile = new File(outDir.toURI().resolve(urlFor(c)));

      CharSink dest = Files.asCharSink(outFile, Charsets.UTF_8);
      try (Writer out = dest.openBufferedStream()) {
        out.write(renderedHtml.get());
      }
    }
  }


  static final String MOJO_INTERFACE_NAME = "org.apache.maven.plugin.Mojo";
  static final String MOJO_ANNOTATION_NAME =
      "org.apache.maven.plugins.annotations.Mojo";
  static final String COMPONENT_ANNOTATION_NAME =
      "org.apache.maven.plugins.annotations.Component";
  static final String PARAMETER_ANNOTATION_NAME =
      "org.apache.maven.plugins.annotations.Parameter";

  void findMojos(ImmutableSet.Builder<String> entryPoints) {
    for (ClassDoc cd : root.classes()) {
      if (cd.isOrdinaryClass() && !cd.isAbstract() && isMojo(cd)) {
        entryPoints.add(cd.qualifiedTypeName());
      }
    }
  }

  boolean isMojo(ClassDoc cd) {
    if (mojoInterface.isPresent()) {
      return cd.subclassOf(mojoInterface.get());
    }
    for (ClassDoc iface : cd.interfaces()) {
      if (MOJO_INTERFACE_NAME.equals(iface.qualifiedTypeName())) {
        return true;
      }
    }
    ClassDoc superCd = cd.superclass();
    return superCd != null && isMojo(superCd);  // interfaces() isn't transitive
  }


  final class Crawl {
    final Map<String, Configurable> configurables = Maps.newLinkedHashMap();
    final Deque<String> needFieldScan = Lists.newLinkedList();

    void crawlAll(Iterable<? extends String> entryPoints) {
      for (String className : entryPoints) {
        Optional<Configurable> c = foundConfigurableClass(
            root.classNamed(className));
        if (c.isPresent()) {
          c.get().tagNames.add("configuration");
        }
      }
      scan();
    }

    Optional<Configurable> foundConfigurableClass(ClassDoc cd) {
      return foundConfigurable(cd, cd);
    }

    Optional<Configurable> foundConfigurable(Doc src, Type t) {
      if (isSelfExplanatory(t)) {
        return Optional.absent();
      }
      if (!(t instanceof ClassDoc)) {
        ClassDoc qcd = root.classNamed(t.qualifiedTypeName());
        if (qcd != null) {
          return foundConfigurable(src, qcd);
        }
        return Optional.absent();
      }

      ClassDoc cd = (ClassDoc) t;

      String qualTypeName = cd.qualifiedTypeName();
      if (!qualTypeName.equals(cd.qualifiedName())) {
        ClassDoc qcd = root.classNamed(qualTypeName);
        if (qcd == null) {
          error(src, "Could not find " + qualTypeName);
          return Optional.absent();
        } else {
          return foundConfigurable(src, qcd);
        }
      }

      if (!cd.isOrdinaryClass() && !cd.isEnum()) {
        return Optional.absent();
      }

      Configurable c = configurables.get(qualTypeName);
      if (c == null) {
        info(src, "Enqueuing " + qualTypeName);
        c = new Configurable(cd);
        configurables.put(qualTypeName, c);
        needFieldScan.addLast(qualTypeName);
      }
      return Optional.of(c);
    }

    void scan() {
      for (String toScan; (toScan = needFieldScan.poll()) != null;) {
        Configurable c = configurables.get(toScan);
        if (c.configurableClass.isEnum()) {
          scanEnum(c);
        } else {
          scan(c);
        }
      }
    }

    void scanEnum(Configurable c) {
      for (FieldDoc value : c.configurableClass.enumConstants()) {
        c.params.put(value.name(), new ParameterInfo(
            value.name(), Optional.of(value), Optional.<MethodDoc>absent()));
      }
    }

    void scan(Configurable c) {
      info(
          c.configurableClass,
          "Scanning configurable " + c.configurableClass.qualifiedName());
      for (ClassDoc cd = c.configurableClass;
           cd != null; cd = cd.superclass()) {
        info(cd, ". scanning type " + cd);
        Map<String, MethodDoc> settersBySetterName = Maps.newLinkedHashMap();
        for (MethodDoc d : cd.methods(false)) {
          if (isSetter(d)) {
            settersBySetterName.put(d.name(), d);
          }
        }

        for (FieldDoc f : cd.fields(false)) {
          if (f.isStatic() || f.isSynthetic()) { continue; }
          String name = f.name();
          Optional<AnnotationDesc> parameter = annotationNamed(
              f, PARAMETER_ANNOTATION_NAME);
          if (parameter.isPresent()) {
            boolean isReadOnly = false;
            for (ElementValuePair evp : parameter.get().elementValues()) {
              switch (evp.element().name()) {
                case "readonly":
                  if (Boolean.TRUE.equals(evp.value().value())) {
                    isReadOnly = true;
                  }
                  break;
                case "name":
                  String value = (String) evp.value().value();
                  if (!"".equals(value)
                      && Objects.equal(
                          value,
                          evp.element().defaultValue().value())) {
                    name = value;
                  }
                  break;
              }
            }
            if (isReadOnly) {
              continue;
            }
          } else if (c.isMojo) {
            continue;
          }
          String setterName = setterNameFor(name);
          // Look for setter defined on the same class.
          MethodDoc setter = settersBySetterName.remove(setterName);

          if (f.isFinal() && setter == null) {
            continue;
          }

          if (!c.params.containsKey(name)) {
            c.params.put(
                name,
                new ParameterInfo(
                    name, Optional.of(f), Optional.fromNullable(setter)));
          }
        }

        if (!c.isMojo) {
          // For each setter not removed by the field loop above, create a
          // field-less parameter.
          for (MethodDoc setter : settersBySetterName.values()) {
            String name = fieldNameForSetter(setter.name());
            if (!c.params.containsKey(name)) {
              c.params.put(name, new ParameterInfo(
                  name, Optional.<FieldDoc>absent(), Optional.of(setter)));
            }
          }
        }
      }
      info(c.configurableClass, ". done scan.  registering " + c.params.size());

      // Recurse to configurable classes found.
      for (ParameterInfo pi : c.params.values()) {
        Type t = pi.getType();
        Optional<Configurable> fc = foundConfigurable(pi.source(), t);
        if (fc.isPresent()) {
          fc.get().tagNames.add(pi.name);
        }
      }
    }
  }


  final class Configurable {
    final ClassDoc configurableClass;
    final boolean isMojo;
    final Map<String, ParameterInfo> params = Maps.newLinkedHashMap();
    /**
     * The field names from which this is referenced which determine the
     * tag name used to create one.
     */
    final SortedSet<String> tagNames = Sets.newTreeSet();

    Configurable(ClassDoc configurableClass) {
      this.configurableClass = configurableClass;

      this.isMojo = isMojo(configurableClass);
    }
  }


  static final class ParameterInfo {
    final String name;
    final Optional<FieldDoc> field;
    final Optional<MethodDoc> setter;

    ParameterInfo(
        String name, Optional<FieldDoc> field, Optional<MethodDoc> setter) {
      Preconditions.checkArgument(field.isPresent() || setter.isPresent());
      this.name = name;
      this.field = field;
      this.setter = setter;

      Preconditions.checkArgument(
          !this.setter.isPresent() || isSetter(this.setter.get()));
    }

    public Doc source() {
      if (setter.isPresent()) {
        // More likely to be public
        return setter.get();
      }
      return field.get();
    }

    String getCommentText() {
      String commentText = null;
      for (MethodDoc setterOrDecl = setter.orNull();
          Strings.isNullOrEmpty(commentText) && setterOrDecl != null;
          setterOrDecl.overriddenMethod()) {
        commentText = setterOrDecl.commentText();
      }
      if (Strings.isNullOrEmpty(commentText) && field.isPresent()) {
        commentText = field.get().commentText();
      }
      return Strings.isNullOrEmpty(commentText) ? null : commentText;
    }

    Type getType() {
      // The setter parameter type trumps the field.
      if (setter.isPresent()) {
        return setter.get().parameters()[0].type();
      }
      Type type = field.get().type();
      if (type.isPrimitive()) {
        return type;
      }
      // Arrays are constructed by glomming together
      // individual configuration elements so strip off 1 layer of array.
      // TODO: I think isMojo affects how this is done.
      if (type.dimension().isEmpty()) {
        return type;
      }
      return type.getElementType();
    }
  }


  static String setterNameFor(String fieldName) {
    int cp0 = fieldName.codePointAt(0);
    return new StringBuilder()
        .append("set")
        .appendCodePoint(Character.toUpperCase(cp0))
        .append(fieldName, Character.charCount(cp0), fieldName.length())
        .toString();
  }

  static boolean isSetter(String methodName) {
    return methodName.length() > 3 && methodName.startsWith("set");
  }

  static boolean isSetter(MethodDoc m) {
    return isSetter(m.name()) && m.parameters().length == 1
        && m.isPublic() && !m.isStatic() && !m.isSynthetic();
  }

  static String fieldNameForSetter(String methodName) {
    Preconditions.checkArgument(isSetter(methodName), methodName);
    int cp3 = methodName.codePointAt(3);
    return new StringBuilder()
        .appendCodePoint(Character.toLowerCase(cp3))
        .append(methodName, 3 + Character.charCount(cp3), methodName.length())
        .toString();
  }

  static Optional<AnnotationDesc> annotationNamed(
      ProgramElementDoc doc, String annotationName) {
    for (AnnotationDesc a : doc.annotations()) {
      if (annotationName.equals(a.annotationType().qualifiedTypeName())) {
        return Optional.of(a);
      }
    }
    return Optional.absent();
  }

  static final Pattern SPECIAL_HANDLING_DOC_ANNOTATION = Pattern.compile(
      "[{]@(code|literal|link|linkplain)\\b([^}]*)[}]"
      );

  /** We use this to convert untrusted HTML into trustworthy SafeHtml */
  static final SafeHtmlMint safeHtmlMint = SafeHtmlMint.fromPolicyFactory(
      new HtmlPolicyBuilder()
      .allowCommonBlockElements()
      .allowCommonInlineFormattingElements()
      .allowStandardUrlProtocols()
      .allowElements("a")
      .allowAttributes("href").onElements("a")
      .requireRelNofollowOnLinks()
      .allowStyling()
      .toFactory());

  /**
   * Massages a Javadoc comment to HTML.
   * TODO: This must be done somewhere by the standard doclet.
   */
  static SafeHtmlProto commentToHtml(String commentText) {
    StringBuilder sb = new StringBuilder();

    int processed = 0, n = commentText.length();
    Matcher m = SPECIAL_HANDLING_DOC_ANNOTATION.matcher(commentText);

    while (m.find()) {
      sb.append(commentText, processed, m.start());
      processed = m.end();

      String body = m.group(2).replaceFirst("^\\s+", "");
      String annot = m.group(1);
      switch (annot) {
        case "code":
          sb.append("<code>")
              .append(SafeHtmls.htmlEscape(body).getSafeHtmlString())
              .append("</code>");
          break;
        case "literal":
          sb.append(SafeHtmls.htmlEscape(body).getSafeHtmlString());
          break;
        case "link":
        case "linkplain":
          // TODO
          processed = m.start();
          break;
      }
    }
    sb.append(commentText, processed, n);

    return SafeHtmls.toProto(safeHtmlMint.sanitize(sb.toString()));
  }

}
