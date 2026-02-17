package com.honeycomb.core.correlation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CorrelationContext} — Reactor context propagation of
 * request IDs and W3C traceparent values.
 *
 * @since 1.4.3
 */
class CorrelationContextTest {

    @Test
    @DisplayName("requestId() reads value from Reactor context")
    void requestIdPresent() {
        StepVerifier.create(
                CorrelationContext.requestId()
                        .contextWrite(ctx -> ctx.put(CorrelationContext.REQUEST_ID_KEY, "req-123"))
        ).expectNext("req-123").verifyComplete();
    }

    @Test
    @DisplayName("requestId() completes empty when key is absent")
    void requestIdAbsent() {
        StepVerifier.create(CorrelationContext.requestId())
                .verifyComplete();
    }

    @Test
    @DisplayName("traceparent() reads value from Reactor context")
    void traceparentPresent() {
        StepVerifier.create(
                CorrelationContext.traceparent()
                        .contextWrite(ctx -> ctx.put(CorrelationContext.TRACEPARENT_KEY, "00-abc-def-01"))
        ).expectNext("00-abc-def-01").verifyComplete();
    }

    @Test
    @DisplayName("traceparent() completes empty when key is absent")
    void traceparentAbsent() {
        StepVerifier.create(CorrelationContext.traceparent())
                .verifyComplete();
    }

    @Test
    @DisplayName("requestIdFrom extracts from snapshot context")
    void requestIdFromContext() {
        Context ctx = Context.of(CorrelationContext.REQUEST_ID_KEY, "snap-id");
        Optional<String> result = CorrelationContext.requestIdFrom(ctx);
        assertTrue(result.isPresent());
        assertEquals("snap-id", result.get());
    }

    @Test
    @DisplayName("requestIdFrom returns empty for missing key")
    void requestIdFromEmpty() {
        Optional<String> result = CorrelationContext.requestIdFrom(Context.empty());
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("traceparentFrom extracts from snapshot context")
    void traceparentFromContext() {
        Context ctx = Context.of(CorrelationContext.TRACEPARENT_KEY, "00-trace-parent-01");
        Optional<String> result = CorrelationContext.traceparentFrom(ctx);
        assertTrue(result.isPresent());
        assertEquals("00-trace-parent-01", result.get());
    }

    @Test
    @DisplayName("withCorrelation writes both keys into context")
    void withCorrelationBothKeys() {
        Context ctx = CorrelationContext.withCorrelation(Context.empty(), "id-1", "tp-1");
        assertEquals("id-1", ctx.get(CorrelationContext.REQUEST_ID_KEY));
        assertEquals("tp-1", ctx.get(CorrelationContext.TRACEPARENT_KEY));
    }

    @Test
    @DisplayName("withCorrelation skips null requestId")
    void withCorrelationNullRequestId() {
        Context ctx = CorrelationContext.withCorrelation(Context.empty(), null, "tp-2");
        assertFalse(ctx.hasKey(CorrelationContext.REQUEST_ID_KEY));
        assertEquals("tp-2", ctx.get(CorrelationContext.TRACEPARENT_KEY));
    }

    @Test
    @DisplayName("withCorrelation skips null traceparent")
    void withCorrelationNullTraceparent() {
        Context ctx = CorrelationContext.withCorrelation(Context.empty(), "id-2", null);
        assertEquals("id-2", ctx.get(CorrelationContext.REQUEST_ID_KEY));
        assertFalse(ctx.hasKey(CorrelationContext.TRACEPARENT_KEY));
    }
}
