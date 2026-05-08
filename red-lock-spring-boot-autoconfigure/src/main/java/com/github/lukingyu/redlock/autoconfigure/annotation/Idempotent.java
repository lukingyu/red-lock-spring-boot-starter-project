package com.github.lukingyu.redlock.autoconfigure.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * Redis key prefix. It can be overridden per business scenario.
     */
    String prefix() default "";

    /**
     * Business key expression, for example {@code #order.id} or {@code #userId}.
     * If empty in a web request, a fingerprint based on the request is used.
     */
    String key() default "";

    /**
     * Backward-compatible alias for early versions.
     */
    @Deprecated(since = "1.1.0", forRemoval = false)
    String spEL() default "";

    /**
     * Lock expiration. Values less than or equal to 0 use the global configuration.
     */
    long timeout() default -1;

    /**
     * Time unit for {@link #timeout()}.
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * Exception message when a duplicate request is detected.
     */
    String message() default "";
}
