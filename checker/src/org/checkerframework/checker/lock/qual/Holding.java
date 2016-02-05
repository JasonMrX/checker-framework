package org.checkerframework.checker.lock.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.checkerframework.framework.qual.PreconditionAnnotation;

/**
 * Indicates a method precondition: the specified expressions must be held
 * when the annotated method is invoked.
 * <p>
 *
 * The argument is a string or set of strings that indicates the expression(s) that must be held,
 * using the <a href="http://types.cs.washington.edu/checker-framework/current/checker-framework-manual.html#java-expressions-as-arguments">syntax
 * of Java expressions</a> described in the manual.
 * The expressions evaluate to an intrinsic (built-in, synchronization)
 * monitor, or an explicit {@link java.util.concurrent.locks.Lock}.
 *
 * @see GuardedBy
 * @checker_framework.manual #lock-checker Lock Checker
 */
@Documented
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
@PreconditionAnnotation(qualifier = LockHeld.class)
public @interface Holding {
    /**
     * The Java expressions that need to be held.
     *
     * @see <a
     *      href="http://types.cs.washington.edu/checker-framework/current/checker-framework-manual.html#java-expressions-as-arguments">Syntax
     *      of Java expressions</a>
     */
    String[] value();
}
