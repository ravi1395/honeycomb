package com.example.honeycomb.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Cell {
    /** Optional name to expose under */
    String value() default "";
    /** Optional port to run this domain on; -1 means not specified */
    int port() default -1;
}
