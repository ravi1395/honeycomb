package com.honeycomb.core.correlation;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Optional;

/**
 * Reactor-context-based correlation ID holder.
 *
 * <p>Stores the request ID and W3C {@code traceparent} values in the
 * Reactor {@link Context} so they flow through the entire reactive
 * chain — including shared-method dispatch, event publishing, and
 * cross-cell HTTP calls.</p>
 *
 * @since 1.4.3
 */
public final class CorrelationContext {
    private CorrelationContext() {}

    /** Reactor context key for the X-Request-Id value. */
    public static final String REQUEST_ID_KEY = "honeycomb.requestId";

    /** Reactor context key for the W3C traceparent value. */
    public static final String TRACEPARENT_KEY = "honeycomb.traceparent";

    /** Reactor context key for the parent span (caller info). */
    public static final String CALLER_SPAN_KEY = "honeycomb.callerSpan";

    /**
     * Retrieve the request ID from the Reactor context.
     */
    public static Mono<String> requestId() {
        return Mono.deferContextual(ctx ->
                ctx.hasKey(REQUEST_ID_KEY)
                        ? Mono.just(ctx.get(REQUEST_ID_KEY))
                        : Mono.empty()
        );
    }

    /**
     * Retrieve the traceparent from the Reactor context.
     */
    public static Mono<String> traceparent() {
        return Mono.deferContextual(ctx ->
                ctx.hasKey(TRACEPARENT_KEY)
                        ? Mono.just(ctx.get(TRACEPARENT_KEY))
                        : Mono.empty()
        );
    }

    /**
     * Read from a snapshot context.
     */
    public static Optional<String> requestIdFrom(Context ctx) {
        return ctx.hasKey(REQUEST_ID_KEY) ? Optional.of(ctx.get(REQUEST_ID_KEY)) : Optional.empty();
    }

    public static Optional<String> traceparentFrom(Context ctx) {
        return ctx.hasKey(TRACEPARENT_KEY) ? Optional.of(ctx.get(TRACEPARENT_KEY)) : Optional.empty();
    }

    /**
     * Write correlation info into a Reactor context.
     */
    public static Context withCorrelation(Context ctx, String requestId, String traceparent) {
        if (requestId != null) ctx = ctx.put(REQUEST_ID_KEY, requestId);
        if (traceparent != null) ctx = ctx.put(TRACEPARENT_KEY, traceparent);
        return ctx;
    }
}
