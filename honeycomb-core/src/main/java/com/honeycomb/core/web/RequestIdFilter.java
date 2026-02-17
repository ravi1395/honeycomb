package com.honeycomb.core.web;

import com.honeycomb.core.correlation.CorrelationContext;
import com.honeycomb.core.util.HoneycombConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Highest-priority {@link WebFilter} that ensures every request and response
 * carries a unique {@code X-Request-Id} and W3C {@code traceparent} header.
 *
 * <p>If the incoming request already has the header, it is preserved;
 * otherwise a new UUID is generated and attached. Both correlation IDs are
 * placed into the Reactor {@link reactor.util.context.Context} via
 * {@link CorrelationContext} and into the SLF4J MDC for structured logging.</p>
 *
 * @since 1.4.3 — extended with traceparent and Reactor context propagation
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter implements WebFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    private static final String TRACEPARENT_HEADER = "traceparent";
    private static final String TRACESTATE_HEADER = "tracestate";

    @Override
    @NonNull
    @SuppressWarnings("null")
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        // --- X-Request-Id ---
        String requestId = exchange.getRequest().getHeaders().getFirst(HoneycombConstants.Headers.REQUEST_ID);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        final String finalRequestId = requestId;

        // --- traceparent (W3C Trace Context) ---
        String traceparent = exchange.getRequest().getHeaders().getFirst(TRACEPARENT_HEADER);
        if (!StringUtils.hasText(traceparent)) {
            traceparent = generateTraceparent();
        }
        final String finalTraceparent = traceparent;

        // tracestate passthrough
        String tracestate = exchange.getRequest().getHeaders().getFirst(TRACESTATE_HEADER);
        final String finalTracestate = tracestate;

        long startNanos = System.nanoTime();

        ServerWebExchange mutated = exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.set(HoneycombConstants.Headers.REQUEST_ID, finalRequestId);
                    headers.set(TRACEPARENT_HEADER, finalTraceparent);
                    if (finalTracestate != null) headers.set(TRACESTATE_HEADER, finalTracestate);
                }))
                .build();

        mutated.getResponse().beforeCommit(() -> {
            mutated.getResponse().getHeaders().set(HoneycombConstants.Headers.REQUEST_ID, finalRequestId);
            mutated.getResponse().getHeaders().set(TRACEPARENT_HEADER, finalTraceparent);
            return Mono.empty();
        });

        return chain.filter(mutated)
                .doFirst(() -> {
                    MDC.put("requestId", finalRequestId);
                    MDC.put("traceparent", finalTraceparent);
                })
                .doFinally(signalType -> {
                    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                    var status = mutated.getResponse().getStatusCode();
                    log.info("requestId={} traceparent={} method={} path={} status={} durationMs={}",
                            finalRequestId,
                            finalTraceparent,
                            mutated.getRequest().getMethod(),
                            mutated.getRequest().getPath().pathWithinApplication().value(),
                            status != null ? status.value() : 0,
                            durationMs);
                    MDC.remove("requestId");
                    MDC.remove("traceparent");
                })
                .contextWrite(ctx -> CorrelationContext.withCorrelation(ctx, finalRequestId, finalTraceparent));
    }

    /**
     * Generate a W3C traceparent header value:
     * {@code 00-<trace-id-32hex>-<parent-id-16hex>-01}
     */
    private static String generateTraceparent() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        String traceId = String.format("%016x%016x", rng.nextLong(), rng.nextLong());
        String parentId = String.format("%016x", rng.nextLong());
        return "00-" + traceId + "-" + parentId + "-01";
    }
}
