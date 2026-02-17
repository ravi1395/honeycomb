package com.honeycomb.core.web;

import com.honeycomb.core.correlation.CorrelationContext;
import com.honeycomb.core.util.HoneycombConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RequestIdFilter} — correlation header injection,
 * Reactor context propagation, and W3C traceparent generation.
 *
 * @since 1.4.3
 */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    /**
     * W3C traceparent format: {@code 00-<32hex>-<16hex>-<2hex>}
     */
    private static final Pattern TRACEPARENT_PATTERN =
            Pattern.compile("^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$");

    @Test
    @DisplayName("generates X-Request-Id when not provided")
    void generatesRequestId() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<String> capturedId = new AtomicReference<>();

        WebFilterChain chain = ex -> {
            capturedId.set(ex.getRequest().getHeaders().getFirst(HoneycombConstants.Headers.REQUEST_ID));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertNotNull(capturedId.get(), "should generate a request ID");
        // UUID format
        assertEquals(36, capturedId.get().length());
    }

    @Test
    @DisplayName("preserves existing X-Request-Id")
    void preservesExistingRequestId() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header(HoneycombConstants.Headers.REQUEST_ID, "existing-id")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<String> capturedId = new AtomicReference<>();

        WebFilterChain chain = ex -> {
            capturedId.set(ex.getRequest().getHeaders().getFirst(HoneycombConstants.Headers.REQUEST_ID));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals("existing-id", capturedId.get());
    }

    @Test
    @DisplayName("generates W3C traceparent when not provided")
    void generatesTraceparent() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<String> capturedTp = new AtomicReference<>();

        WebFilterChain chain = ex -> {
            capturedTp.set(ex.getRequest().getHeaders().getFirst("traceparent"));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertNotNull(capturedTp.get(), "should generate traceparent");
        assertTrue(TRACEPARENT_PATTERN.matcher(capturedTp.get()).matches(),
                "traceparent should match W3C format: " + capturedTp.get());
    }

    @Test
    @DisplayName("preserves existing traceparent header")
    void preservesExistingTraceparent() {
        String existing = "00-abcdef0123456789abcdef0123456789-1234567890abcdef-01";
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header("traceparent", existing)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<String> capturedTp = new AtomicReference<>();

        WebFilterChain chain = ex -> {
            capturedTp.set(ex.getRequest().getHeaders().getFirst("traceparent"));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(existing, capturedTp.get());
    }

    @Test
    @DisplayName("correlation IDs are placed into Reactor context")
    void contextPropagation() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header(HoneycombConstants.Headers.REQUEST_ID, "ctx-test-id")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<String> ctxRequestId = new AtomicReference<>();
        AtomicReference<String> ctxTraceparent = new AtomicReference<>();

        WebFilterChain chain = ex -> Mono.deferContextual(ctx -> {
            ctxRequestId.set(ctx.getOrDefault(CorrelationContext.REQUEST_ID_KEY, null));
            ctxTraceparent.set(ctx.getOrDefault(CorrelationContext.TRACEPARENT_KEY, null));
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals("ctx-test-id", ctxRequestId.get());
        assertNotNull(ctxTraceparent.get(), "traceparent should be in context");
    }

    @Test
    @DisplayName("response headers include X-Request-Id and traceparent")
    void responseHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header(HoneycombConstants.Headers.REQUEST_ID, "resp-id")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> {
            // Trigger the beforeCommit callback by writing to the response
            ex.getResponse().setRawStatusCode(200);
            return ex.getResponse().setComplete();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
        assertEquals("resp-id", responseHeaders.getFirst(HoneycombConstants.Headers.REQUEST_ID));
        assertNotNull(responseHeaders.getFirst("traceparent"));
    }
}
