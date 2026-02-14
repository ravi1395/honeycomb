package com.honeycomb.core.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the CRUD operation type for a service cell method.
 *
 * <p>Used on methods within a {@link Cell}-annotated service bean to map
 * them to the appropriate HTTP verb under
 * {@code /honeycomb/service/{cell}/{method}}.</p>
 *
 * @see MethodOp
 * @see com.honeycomb.core.service.ServiceCellRegistry
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MethodType {
    MethodOp value();
    /**
     * Optional path segment override. If empty, the Java method name is used.
     * This is treated as a single path segment (no slashes).
     */
    String path() default "";
}
