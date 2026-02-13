package com.example.honeycomb.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Optional method-level alias for typed sharedwall client interfaces.
 * If absent, the Java method name is used as the sharedwall method name.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SharedwallCall {
    String value();
    String version() default "v1";
}
