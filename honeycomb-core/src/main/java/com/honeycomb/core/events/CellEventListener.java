package com.honeycomb.core.events;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a cell event listener.
 * The annotated method is invoked when events matching the specified type(s) arrive.
 *
 * <p><b>Added in v1.3</b> — declarative event handling for the cell event bus.</p>
 *
 * <p>
 * The method should accept a single {@link com.honeycomb.core.dto.CellEvent} parameter
 * and may return void, {@code Mono<Void>}, or {@code Mono<?>}.
 *
 * <pre>
 * &#64;CellEventListener(CellEvent.TYPE_ITEM_CREATED)
 * public Mono&lt;Void&gt; onItemCreated(CellEvent event) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CellEventListener {

    /**
     * Event type(s) this listener handles.
     * Empty means all event types (wildcard listener).
     */
    String[] value() default {};

    /**
     * Optional: only listen to events from specific source cells.
     * Empty means all cells.
     */
    String[] fromCells() default {};

    /**
     * Ordering hint. Lower values run first.
     */
    int order() default 0;
}
