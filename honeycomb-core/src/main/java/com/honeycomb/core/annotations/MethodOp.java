package com.honeycomb.core.annotations;

/**
 * Enumerates the supported operation types for {@link MethodType}-annotated
 * service cell methods. Maps to HTTP verbs:
 * <ul>
 *   <li>{@code CREATE} → POST</li>
 *   <li>{@code READ} → GET</li>
 *   <li>{@code UPDATE} → PUT</li>
 *   <li>{@code DELETE} → DELETE</li>
 *   <li>{@code CUSTOM} → any custom verb</li>
 *   <li>{@code SHARED} → exposed as shared method</li>
 * </ul>
 */
public enum MethodOp {
    CREATE,
    READ,
    UPDATE,
    DELETE,
    CUSTOM,
    SHARED
}
