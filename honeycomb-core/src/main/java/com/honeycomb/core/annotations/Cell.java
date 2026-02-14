package com.honeycomb.core.annotations;

import com.honeycomb.core.util.HoneycombConstants;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a Honeycomb cell — a lightweight, runtime-discoverable model component.
 *
 * <p>Annotated classes are automatically registered in the {@code CellRegistry} at startup,
 * exposed through the uniform CRUD API at {@code /honeycomb/models/{name}/items},
 * and optionally hosted on a dedicated per-cell HTTP server.</p>
 *
 * @see com.honeycomb.core.service.CellRegistry
 * @see com.honeycomb.core.service.CellServerManager
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Cell {
    /** Optional name to expose under */
    String value() default HoneycombConstants.Messages.EMPTY;
    /** Optional port to run this cell on; -1 means not specified */
    int port() default -1;
}
