package com.example.honeycomb.annotations;

import com.example.honeycomb.util.HoneycombConstants;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Sharedwall {
    /** Optional alias or external name for the shared method */
    String value() default HoneycombConstants.Messages.EMPTY;
    /** Optional list of cell names that are allowed to call this shared method. Empty means allow all. Use "*" to explicitly allow all. */
    String[] allowedFrom() default {};
}
