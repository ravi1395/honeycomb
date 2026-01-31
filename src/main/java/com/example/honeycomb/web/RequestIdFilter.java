package com.example.honeycomb.web;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import org.springframework.lang.NonNull;

import java.util.UUID;

@Component
@SuppressWarnings("null")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter implements WebFilter {
    public static final String HEADER = "X-Request-Id";

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        String finalId = requestId;
        exchange.getResponse().getHeaders().set(HEADER, finalId);
        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put(HEADER, finalId))
                .doOnEach(signal -> MDC.put(HEADER, finalId))
                .doFinally(signal -> MDC.remove(HEADER));
    }
}
