package com.google.common.html.plugin.soy;

import java.lang.reflect.InvocationTargetException;

import org.apache.maven.plugin.MojoExecutionException;


/**
 * Avoids maintenance hazards due to doing reflective operations on classes
 * loaded in a separate classloader by allowing both direct and indirect
 * invocation code to colocate.
 * <p>
 * HACK:
 * When compiling Soy to Java, we load soy in a separate class loader so that
 * {@link Class#forName} calls made by the jbcsrc backend can load generate
 * protobuf message classes that have been compiled under
 * {@code target/classes}.
 * <p>
 * To keep the reflective code in-sync with the regular code, we wrap separable
 * sequence of method calls into an operation that has both the direct version
 * and the reflective version, so that Java's normal type checking will tell
 * us when the reflective version's assumptions need to have changed.
 */
interface ReflectionableOperation<DIRECT_INPUT, RESULT> {

  RESULT direct(DIRECT_INPUT inp) throws MojoExecutionException;

  Object reflect(ClassLoader cl, Object inp)
  throws MojoExecutionException, ReflectiveOperationException;

  String logDescription();


  static final class Util {
    private Util() {}

    static
    <I, O>
    O direct(
        ReflectionableOperation<I, O> op,
        I inp)
    throws MojoExecutionException {
      return op.direct(inp);
    }

    static
    Object reflect(
        ClassLoader cl,
        ReflectionableOperation<?, ?> op,
        Object inp)
    throws MojoExecutionException {
      try {
        return op.reflect(cl, inp);
      } catch (InvocationTargetException ex) {
        throw new MojoExecutionException(
            op.logDescription() + " failed", ex.getTargetException());
      } catch (ReflectiveOperationException ex) {
        throw new MojoExecutionException(
            op.logDescription() + " failed", ex);
      }
    }

    static <I, M0, O>
    ReflectionableOperation<I, O> chain(
        final ReflectionableOperation<? super I,  ? extends M0> op0,
        final ReflectionableOperation<? super M0, ? extends O>  op1) {
      return new ReflectionableOperation<I, O>() {

        @Override
        public O direct(I inp) throws MojoExecutionException {
          return op1.direct(op0.direct(inp));
        }

        @Override
        public Object reflect(ClassLoader cl, Object inp)
        throws MojoExecutionException, ReflectiveOperationException {
          return op1.reflect(cl, op0.reflect(cl, inp));
        }

        @Override
        public String logDescription() {
          return op0.logDescription() + ";" + op1.logDescription();
        }
      };
    }

    static <I, M0, M1, O>
    ReflectionableOperation<I, O> chain(
        final ReflectionableOperation<? super I,  ? extends M0> op0,
        final ReflectionableOperation<? super M0, ? extends M1> op1,
        final ReflectionableOperation<? super M1, ? extends O>  op2) {
      return new ReflectionableOperation<I, O>() {

        @Override
        public O direct(I inp) throws MojoExecutionException {
          return op2.direct(op1.direct(op0.direct(inp)));
        }

        @Override
        public Object reflect(ClassLoader cl, Object inp)
        throws MojoExecutionException, ReflectiveOperationException {
          return op2.reflect(cl, op1.reflect(cl, op0.reflect(cl, inp)));
        }

        @Override
        public String logDescription() {
          return op0.logDescription() + ";" + op1.logDescription()
              + ";" + op2.logDescription();
        }
      };
    }

    static <I, M0, M1, M2, O>
    ReflectionableOperation<I, O> chain(
        final ReflectionableOperation<? super I,  ? extends M0> op0,
        final ReflectionableOperation<? super M0, ? extends M1> op1,
        final ReflectionableOperation<? super M1, ? extends M2> op2,
        final ReflectionableOperation<? super M2, ? extends O>  op3) {
      return new ReflectionableOperation<I, O>() {

        @Override
        public O direct(I inp) throws MojoExecutionException {
          return op3.direct(op2.direct(op1.direct(op0.direct(inp))));
        }

        @Override
        public Object reflect(ClassLoader cl, Object inp)
        throws MojoExecutionException, ReflectiveOperationException {
          return op3.reflect(
              cl,
              op2.reflect(
                  cl,
                  op1.reflect(
                      cl,
                      op0.reflect(cl, inp))));
        }

        @Override
        public String logDescription() {
          return op0.logDescription() + ";" + op1.logDescription()
              + ";" + op2.logDescription() + ";" + op3.logDescription();
        }
      };
    }
  }
}
