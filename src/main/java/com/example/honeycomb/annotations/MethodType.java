package com.example.honeycomb.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
