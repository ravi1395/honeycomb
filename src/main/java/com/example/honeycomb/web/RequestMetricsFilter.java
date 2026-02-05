package com.example.honeycomb.web;

import com.example.honeycomb.service.RequestMetricsService;
import com.example.honeycomb.util.HoneycombConstants;
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
        if (!(path.startsWith(HoneycombConstants.Paths.HONEYCOMB_BASE)
            || path.startsWith(HoneycombConstants.Paths.CELLS_BASE))) {
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
        if (path == null) return HoneycombConstants.Messages.UNKNOWN;
        if (path.startsWith(HoneycombConstants.Paths.HONEYCOMB_MODELS
            + HoneycombConstants.Names.SEPARATOR_SLASH)) {
            return HoneycombConstants.Paths.HONEYCOMB_MODELS;
        }
        if (path.startsWith(HoneycombConstants.Paths.HONEYCOMB_CELLS)) {
            return HoneycombConstants.Paths.HONEYCOMB_CELLS;
        }
        if (path.startsWith(HoneycombConstants.Paths.HONEYCOMB_SHARED)) {
            return HoneycombConstants.Paths.HONEYCOMB_SHARED;
        }
        if (path.startsWith(HoneycombConstants.Paths.HONEYCOMB_METRICS)) {
            return HoneycombConstants.Paths.HONEYCOMB_METRICS;
        }
        if (path.startsWith(HoneycombConstants.Paths.HONEYCOMB_AUDIT)) {
            return HoneycombConstants.Paths.HONEYCOMB_AUDIT;
        }
        if (path.startsWith(HoneycombConstants.Paths.HONEYCOMB_ADMIN)) {
            return HoneycombConstants.Paths.HONEYCOMB_ADMIN;
        }
        if (path.startsWith(HoneycombConstants.Paths.HONEYCOMB_ACTUATOR)) {
            return HoneycombConstants.Paths.HONEYCOMB_ACTUATOR;
        }
        if (path.startsWith(HoneycombConstants.Paths.HONEYCOMB_SWAGGER)) {
            return HoneycombConstants.Paths.HONEYCOMB_SWAGGER;
        }
        if (path.startsWith(HoneycombConstants.Paths.HONEYCOMB_API_DOCS)) {
            return HoneycombConstants.Paths.HONEYCOMB_API_DOCS;
        }
        if (path.startsWith(HoneycombConstants.Paths.CELLS_BASE)) {
            return HoneycombConstants.Paths.CELLS_BASE;
        }
        if (path.startsWith(HoneycombConstants.Paths.HONEYCOMB_BASE)) {
            return HoneycombConstants.Paths.HONEYCOMB_BASE;
        }
        return HoneycombConstants.Messages.UNKNOWN;
    }
}
