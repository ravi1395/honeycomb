package com.example.honeycomb.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SharedwallResult {
    SharedwallEnvelopeMode mode() default SharedwallEnvelopeMode.FIRST_RESULT;
    String cell() default "";
}
