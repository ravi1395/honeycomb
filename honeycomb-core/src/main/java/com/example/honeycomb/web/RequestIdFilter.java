package com.example.honeycomb.web;

import com.example.honeycomb.util.HoneycombConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter implements WebFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    @NonNull
    @SuppressWarnings("null")
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(HoneycombConstants.Headers.REQUEST_ID);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        final String finalRequestId = requestId;
        long startNanos = System.nanoTime();

        ServerWebExchange mutated = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(HoneycombConstants.Headers.REQUEST_ID, finalRequestId)))
                .build();

        mutated.getResponse().beforeCommit(() -> {
            mutated.getResponse().getHeaders().set(HoneycombConstants.Headers.REQUEST_ID, finalRequestId);
            return Mono.empty();
        });

        return chain.filter(mutated)
                .doFinally(signalType -> {
                    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                    var status = mutated.getResponse().getStatusCode();
                    log.info("requestId={} method={} path={} status={} durationMs={}",
                            finalRequestId,
                            mutated.getRequest().getMethod(),
                            mutated.getRequest().getPath().pathWithinApplication().value(),
                            status != null ? status.value() : 0,
                            durationMs);
                });
    }
}
