package com.google.closure.plugin.soy;

import org.apache.maven.plugin.logging.Log;

import com.google.closure.plugin.common.Options;
import com.google.template.soy.jssrc.SoyJsSrcOptions;

/**
 * JavaScript-backend related options for the Soy compiler.
 */
public final class Js extends Options {

  private static final long serialVersionUID = 8412310172202543780L;

  /**
   * Whether we should generate JSDoc with type info for the Closure Compiler.
   */
  public Boolean shouldGenerateJsdoc = true;

  /** Whether we should generate code to provide/require Soy namespaces. */
  public Boolean shouldProvideRequireSoyNamespaces = true;

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
  public Boolean shouldGenerateGoogModules;

  /**
   * Whether we should generate Closure Library message definitions
   * (i.e. goog.getMsg).
   */
  public Boolean shouldGenerateGoogMsgDefs;

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
  public Boolean useGoogIsRtlForBidiGlobalDir;


  /**
   * Creates JavaScript source backend-specific options.
   */
  public SoyJsSrcOptions toSoyJsSrcOptions(
      @SuppressWarnings("unused") Log log) {
    SoyJsSrcOptions jsSrcOptions = new SoyJsSrcOptions();

    // Do this first since it is initialized to true in the default constructor
    // and can conflict with some other options.
    if (shouldDeclareTopLevelNamespaces != null) {
      jsSrcOptions.setShouldDeclareTopLevelNamespaces(
          shouldDeclareTopLevelNamespaces);
    }

    if (bidiGlobalDir != null) {
      jsSrcOptions.setBidiGlobalDir(this.bidiGlobalDir.flagValue);
    }
    if (shouldGenerateJsdoc != null) {
      jsSrcOptions.setShouldGenerateJsdoc(shouldGenerateJsdoc);
    }
    if (shouldProvideRequireSoyNamespaces != null) {
      jsSrcOptions.setShouldProvideRequireSoyNamespaces(
          shouldProvideRequireSoyNamespaces);
    }
    if (shouldProvideRequireJsFunctions != null) {
       jsSrcOptions.setShouldProvideRequireJsFunctions(
           shouldProvideRequireJsFunctions);
    }
    if (shouldProvideBothSoyNamespacesAndJsFunctions != null) {
       jsSrcOptions.setShouldProvideBothSoyNamespacesAndJsFunctions(
           shouldProvideBothSoyNamespacesAndJsFunctions);
    }
    if (shouldGenerateGoogModules != null) {
      jsSrcOptions.setShouldGenerateGoogModules(shouldGenerateGoogModules);
    }
    if (shouldGenerateGoogMsgDefs != null) {
      jsSrcOptions.setShouldGenerateGoogMsgDefs(shouldGenerateGoogMsgDefs);
    }
    if (googMsgsAreExternal != null) {
       jsSrcOptions.setGoogMsgsAreExternal(googMsgsAreExternal);
    }
    if (useGoogIsRtlForBidiGlobalDir != null) {
      jsSrcOptions.setUseGoogIsRtlForBidiGlobalDir(
          useGoogIsRtlForBidiGlobalDir);
    }

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



  @Override
  protected void createLazyDefaults() {
    // Done
  }



  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((bidiGlobalDir == null) ? 0 : bidiGlobalDir.hashCode());
    result = prime * result + ((googMsgsAreExternal == null) ? 0 : googMsgsAreExternal.hashCode());
    result = prime * result
        + ((shouldDeclareTopLevelNamespaces == null) ? 0 : shouldDeclareTopLevelNamespaces.hashCode());
    result = prime * result + ((shouldGenerateGoogModules == null) ? 0 : shouldGenerateGoogModules.hashCode());
    result = prime * result + ((shouldGenerateGoogMsgDefs == null) ? 0 : shouldGenerateGoogMsgDefs.hashCode());
    result = prime * result + ((shouldGenerateJsdoc == null) ? 0 : shouldGenerateJsdoc.hashCode());
    result = prime * result + ((shouldProvideBothSoyNamespacesAndJsFunctions == null) ? 0
        : shouldProvideBothSoyNamespacesAndJsFunctions.hashCode());
    result = prime * result
        + ((shouldProvideRequireJsFunctions == null) ? 0 : shouldProvideRequireJsFunctions.hashCode());
    result = prime * result
        + ((shouldProvideRequireSoyNamespaces == null) ? 0 : shouldProvideRequireSoyNamespaces.hashCode());
    result = prime * result + ((useGoogIsRtlForBidiGlobalDir == null) ? 0 : useGoogIsRtlForBidiGlobalDir.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    Js other = (Js) obj;
    if (bidiGlobalDir != other.bidiGlobalDir) {
      return false;
    }
    if (googMsgsAreExternal == null) {
      if (other.googMsgsAreExternal != null) {
        return false;
      }
    } else if (!googMsgsAreExternal.equals(other.googMsgsAreExternal)) {
      return false;
    }
    if (shouldDeclareTopLevelNamespaces == null) {
      if (other.shouldDeclareTopLevelNamespaces != null) {
        return false;
      }
    } else if (!shouldDeclareTopLevelNamespaces.equals(other.shouldDeclareTopLevelNamespaces)) {
      return false;
    }
    if (shouldGenerateGoogModules == null) {
      if (other.shouldGenerateGoogModules != null) {
        return false;
      }
    } else if (!shouldGenerateGoogModules.equals(other.shouldGenerateGoogModules)) {
      return false;
    }
    if (shouldGenerateGoogMsgDefs == null) {
      if (other.shouldGenerateGoogMsgDefs != null) {
        return false;
      }
    } else if (!shouldGenerateGoogMsgDefs.equals(other.shouldGenerateGoogMsgDefs)) {
      return false;
    }
    if (shouldGenerateJsdoc == null) {
      if (other.shouldGenerateJsdoc != null) {
        return false;
      }
    } else if (!shouldGenerateJsdoc.equals(other.shouldGenerateJsdoc)) {
      return false;
    }
    if (shouldProvideBothSoyNamespacesAndJsFunctions == null) {
      if (other.shouldProvideBothSoyNamespacesAndJsFunctions != null) {
        return false;
      }
    } else
      if (!shouldProvideBothSoyNamespacesAndJsFunctions.equals(other.shouldProvideBothSoyNamespacesAndJsFunctions)) {
      return false;
    }
    if (shouldProvideRequireJsFunctions == null) {
      if (other.shouldProvideRequireJsFunctions != null) {
        return false;
      }
    } else if (!shouldProvideRequireJsFunctions.equals(other.shouldProvideRequireJsFunctions)) {
      return false;
    }
    if (shouldProvideRequireSoyNamespaces == null) {
      if (other.shouldProvideRequireSoyNamespaces != null) {
        return false;
      }
    } else if (!shouldProvideRequireSoyNamespaces.equals(other.shouldProvideRequireSoyNamespaces)) {
      return false;
    }
    if (useGoogIsRtlForBidiGlobalDir == null) {
      if (other.useGoogIsRtlForBidiGlobalDir != null) {
        return false;
      }
    } else if (!useGoogIsRtlForBidiGlobalDir.equals(other.useGoogIsRtlForBidiGlobalDir)) {
      return false;
    }
    return true;
  }
}