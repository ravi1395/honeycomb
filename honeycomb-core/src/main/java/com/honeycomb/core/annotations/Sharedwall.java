package com.honeycomb.core.annotations;

import com.honeycomb.core.util.HoneycombConstants;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exposes a method (or all methods on a type/interface) as a shared method
 * invokable via {@code POST /honeycomb/shared/{name}}.
 *
 * <p>When placed on a class or interface, all public methods become shared.
 * When placed on individual methods, only those methods are shared.
 * Access can be restricted via {@link #allowedFrom()} and versioned
 * with {@link #version()} for contract evolution.</p>
 *
 * @see com.honeycomb.core.service.SharedwallMethodCache
 * @see com.honeycomb.core.web.SharedwallDispatcherController
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Sharedwall {
    /** Optional alias or external name for the shared method */
    String value() default HoneycombConstants.Messages.EMPTY;
    /** Contract version for this shared method. */
    String version() default "v1";
    /** Optional list of cell names that are allowed to call this shared method. Empty means allow all. Use "*" to explicitly allow all. */
    String[] allowedFrom() default {};
}
