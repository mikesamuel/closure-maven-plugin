package com.google.common.html.plugin.soy;

import java.lang.reflect.Method;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.html.plugin.common.Options;
import com.google.common.html.plugin.common.OptionsUtils;
import com.google.common.html.plugin.common.SourceOptions;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.SoyFileSet.Builder;

/**
 * An ID that must be unique among a bundle of options of the same kind used
 * in a compilation batch.
 */
public final class SoyOptions extends SourceOptions {

  private static final long serialVersionUID = -7199213790460881298L;

  /** whether to allow external calls (calls to undefined templates). */
  public Boolean allowExternalCalls;

  /** A JSON map of global names to values. */
  public String compileTimeGlobals;

  /**
   * true to force strict autoescaping. Enabling will cause compile time
   * exceptions if non-strict autoescaping is used in namespaces or templates.
   */
  public Boolean strictAutoescapingRequired;

  /** JS backend-specific options to the soy compiler. */
  public Js[] js;


  @Override
  public SoyOptions clone() throws CloneNotSupportedException {
    return (SoyOptions) super.clone();
  }


  /**
   * Creates a soy file set builder from this option sets fields.
   */
  SoyFileSet.Builder toSoyFileSetBuilderDirect(Log log)
  throws MojoExecutionException {
    ReflectionableOperation<Void, SoyFileSet.Builder> makeSoyFileSet
        = getBuilderMaker(log);
    return ReflectionableOperation.Util.direct(makeSoyFileSet, null);
  }

  /**
   * Like {@link #toSoyFileSetBuilderDirect} but can operate on a version of the
   * Soy classes loaded in a custom classloader.
   */
  Object toSoyFileSetBuilderReflective(Log log, ClassLoader cl)
  throws MojoExecutionException {
    ReflectionableOperation<Void, SoyFileSet.Builder> makeSoyFileSet
        = getBuilderMaker(log);
    return ReflectionableOperation.Util.reflect(cl, makeSoyFileSet, null);
  }

  private ReflectionableOperation<Void, SoyFileSet.Builder>
  getBuilderMaker(final Log log) {
    class MakeSoyFileSetBuilder
    implements ReflectionableOperation<Void, SoyFileSet.Builder> {

      @Override
      public Builder direct(Void inp) throws MojoExecutionException {
        return SoyFileSet.builder();
      }

      @Override
      public Object reflect(ClassLoader cl, Object inp)
      throws MojoExecutionException, ReflectiveOperationException {
        Class<?> builderClass = cl.loadClass(SoyFileSet.class.getName());
        Method builderMethod = builderClass.getDeclaredMethod("builder");
        return builderMethod.invoke(null);
      }

      @Override
      public String logDescription() {
        return "SoyFileSet.builder()";
      }
    }

    abstract class AbstractSetter
    implements ReflectionableOperation<SoyFileSet.Builder, SoyFileSet.Builder> {
      @Override
      public String logDescription() {
        return getClass().getSimpleName();
      }
    }

    class SetAllowExternalCalls extends AbstractSetter {
      @Override
      public Builder direct(Builder builder) throws MojoExecutionException {
        if (allowExternalCalls != null) {
          builder.setAllowExternalCalls(allowExternalCalls.booleanValue());
        }
        return builder;
      }

      @Override
      public Object reflect(ClassLoader cl, Object builder)
      throws MojoExecutionException, ReflectiveOperationException {
        if (allowExternalCalls != null) {
          Method setter = builder.getClass().getMethod(
              "setAllowExternalCalls", Boolean.TYPE);
          setter.invoke(builder, allowExternalCalls);
        }
        return builder;
      }
    }

    class SetCompileTimeGlobals extends AbstractSetter {

      private Optional<ImmutableMap<String, Object>> getGlobals() {
        if (compileTimeGlobals != null) {
          return OptionsUtils.keyValueMapFromJson(log, compileTimeGlobals);
        }
        return Optional.absent();
      }

      @Override
      public Builder direct(Builder builder) throws MojoExecutionException {
        Optional<ImmutableMap<String, Object>> globals = getGlobals();
        if (globals.isPresent()) {
          Map<String, ?> globalsMap = globals.get();
          builder.setCompileTimeGlobals(globalsMap);
        }
        return builder;
      }

      @Override
      public Object reflect(ClassLoader cl, Object builder)
      throws MojoExecutionException, ReflectiveOperationException {
        Optional<ImmutableMap<String, Object>> globals = getGlobals();
        if (globals.isPresent()) {
          Map<String, ?> globalsMap = globals.get();
          Method setter = builder.getClass().getMethod(
              "setCompileTimeGlobals", Map.class);
          setter.invoke(builder, globalsMap);
        }
        return builder;
      }
    }


    class SetStrictAutoescapingRequired extends AbstractSetter {
      @Override
      public Builder direct(Builder builder) throws MojoExecutionException {
        if (strictAutoescapingRequired != null) {
          builder.setStrictAutoescapingRequired(
              strictAutoescapingRequired.booleanValue());
        }
        return builder;
      }

      @Override
      public Object reflect(ClassLoader cl, Object builder)
      throws MojoExecutionException, ReflectiveOperationException {
        if (strictAutoescapingRequired != null) {
          Method setter = builder.getClass().getMethod(
              "setStrictAutoescapingRequired", Boolean.TYPE);
          setter.invoke(builder, strictAutoescapingRequired);
        }
        return builder;
      }
    }

    return ReflectionableOperation.Util.chain(
        new MakeSoyFileSetBuilder(),
        new SetAllowExternalCalls(),
        new SetCompileTimeGlobals(),
        new SetStrictAutoescapingRequired());
  }


  @Override
  protected void createLazyDefaults() {
    if (this.js == null || js.length == 0) {
      this.js = new Js[] { new Js() };
    }
  }

  @Override
  protected ImmutableList<Js> getSubOptions() {
    return this.js != null
        ? ImmutableList.copyOf(this.js)
        : ImmutableList.<Js>of();
  }

  @Override
  protected void setSubOptions(ImmutableList<? extends Options> newOptions) {
    ImmutableList.Builder<Js> newJs = ImmutableList.builder();
    for (Options o : newOptions) {
      newJs.add((Js) o);
    }
    this.js = newJs.build().toArray(new Js[newOptions.size()]);
  }


  @Override
  protected ImmutableList<String> sourceExtensions() {
    return ImmutableList.of("soy");
  }
}
