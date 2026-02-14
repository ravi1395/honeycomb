package com.honeycomb.core.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method-level annotation controlling envelope unwrapping mode and
 * the target cell for a typed sharedwall client method.
 *
 * @see SharedwallEnvelopeMode
 * @see TypedSharedwallClientFactory
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SharedwallResult {
    SharedwallEnvelopeMode mode() default SharedwallEnvelopeMode.FIRST_RESULT;
    String cell() default "";
}
