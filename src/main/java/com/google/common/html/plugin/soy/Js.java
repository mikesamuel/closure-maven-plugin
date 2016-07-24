package com.google.common.html.plugin.soy;

import org.apache.maven.plugin.logging.Log;

import com.google.common.html.plugin.Options;
import com.google.template.soy.jssrc.SoyJsSrcOptions;

/**
 * JavaScript-backend related options for the Soy compiler.
 */
public final class Js extends Options {

  private static final long serialVersionUID = 8412310172202543780L;

  /**
   * Whether we should generate JSDoc with type info for the Closure Compiler.
   */
  public boolean shouldGenerateJsdoc = true;

  /** Whether we should generate code to provide/require Soy namespaces. */
  public boolean shouldProvideRequireSoyNamespaces = true;

  /**
   * Whether we should generate code to provide/require template JS functions.
   */
  public Boolean shouldProvideRequireJsFunctions;

  /**
   * Whether we should generate code to provide both Soy namespaces and JS
   * functions.
   */
  public Boolean shouldProvideBothSoyNamespacesAndJsFunctions;

  /** Whether we should generate code to declare the top level namespace. */
  public Boolean shouldDeclareTopLevelNamespaces;

  /** Whether we should generate code to declare goog.modules. */
  public boolean shouldGenerateGoogModules = true;

  /**
   * Whether we should generate Closure Library message definitions
   * (i.e. goog.getMsg).
   */
  public boolean shouldGenerateGoogMsgDefs = true;

  /**
   * Whether the Closure Library messages are external, i.e.
   * "MSG_EXTERNAL_[soyGeneratedMsgId]".
   */
  public Boolean googMsgsAreExternal;

  /**
   * The bidi global directionality as a static value,
   * If unspecified, and useGoogIsRtlForBidiGlobalDir is false, the bidi
   * global directionality will actually be inferred from the message bundle
   * locale.
   * This must not be the case when shouldGenerateGoogMsgDefs is
   * true, but is the recommended mode of operation otherwise.
   */
  public TextDirection bidiGlobalDir;

  /**
   * Whether to determine the bidi global direction at template runtime by
   * evaluating goog.i18n.bidi.IS_RTL. May only be true when both
   * shouldGenerateGoogMsgDefs and either shouldProvideRequireSoyNamespaces or
   * shouldProvideRequireJsFunctions is true.
   */
  public boolean useGoogIsRtlForBidiGlobalDir = true;


  /**
   * Creates JavaScript source backend-specific options.
   */
  public SoyJsSrcOptions toSoyJsSrcOptions(
      @SuppressWarnings("unused") Log log) {
    SoyJsSrcOptions jsSrcOptions = new SoyJsSrcOptions();

    if (this.bidiGlobalDir != null) {
      jsSrcOptions.setBidiGlobalDir(this.bidiGlobalDir.flagValue);
    }
    jsSrcOptions.setShouldGenerateJsdoc(shouldGenerateJsdoc);
    jsSrcOptions.setShouldProvideRequireSoyNamespaces(
        shouldProvideRequireSoyNamespaces);
    if (shouldProvideRequireJsFunctions != null) {
       jsSrcOptions.setShouldProvideRequireJsFunctions(
           shouldProvideRequireJsFunctions);
    }
    if (shouldProvideBothSoyNamespacesAndJsFunctions != null) {
       jsSrcOptions.setShouldProvideBothSoyNamespacesAndJsFunctions(
           shouldProvideBothSoyNamespacesAndJsFunctions);
    }
    if (shouldDeclareTopLevelNamespaces != null) {
       jsSrcOptions.setShouldDeclareTopLevelNamespaces(
           shouldDeclareTopLevelNamespaces);
    }
    jsSrcOptions.setShouldGenerateGoogModules(shouldGenerateGoogModules);
    jsSrcOptions.setShouldGenerateGoogMsgDefs(shouldGenerateGoogMsgDefs);
    if (googMsgsAreExternal != null) {
       jsSrcOptions.setGoogMsgsAreExternal(googMsgsAreExternal);
    }
    jsSrcOptions.setUseGoogIsRtlForBidiGlobalDir(
        useGoogIsRtlForBidiGlobalDir);

    return jsSrcOptions;
  }



  /**
   * The directionality of the text nodes in this.
   */
  public enum TextDirection {
    /** left-to-right. */
    LTR(1),
    /** right-to-left. */
    RTL(-1),
    ;

    /** Value that has a documented meaning to soy options objects. */
    public final int flagValue;

    TextDirection(int flagValue) {
      this.flagValue = flagValue;
    }
  }



  @Override
  public Js clone() throws CloneNotSupportedException {
    return (Js) super.clone();
  }
}