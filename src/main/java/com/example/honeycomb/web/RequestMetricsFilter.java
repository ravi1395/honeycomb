package com.example.honeycomb.web;

import com.example.honeycomb.service.RequestMetricsService;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Component
@SuppressWarnings("null")
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class RequestMetricsFilter implements WebFilter {
    private final RequestMetricsService metricsService;

    public RequestMetricsFilter(RequestMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!(path.startsWith("/honeycomb") || path.startsWith("/cells"))) {
            return chain.filter(exchange);
        }
        Instant start = Instant.now();
        String cell = CellPathResolver.resolveCell(path);
        String route = simplifyRoute(path);
        return chain.filter(exchange)
                .doOnTerminate(() -> {
                    HttpStatusCode status = exchange.getResponse().getStatusCode();
                    int code = status == null ? 200 : status.value();
                    metricsService.record(cell, route, code, Duration.between(start, Instant.now()));
                });
    }

    private String simplifyRoute(String path) {
        if (path == null) return "unknown";
        if (path.startsWith("/honeycomb/models/")) return "/honeycomb/models";
        if (path.startsWith("/honeycomb/cells")) return "/honeycomb/cells";
        if (path.startsWith("/honeycomb/shared")) return "/honeycomb/shared";
        if (path.startsWith("/cells")) return "/cells";
        return path;
    }
}
