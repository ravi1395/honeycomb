package com.example.honeycomb.web;

import com.example.honeycomb.config.HoneycombRateLimiterProperties;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@SuppressWarnings("null")
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter implements WebFilter {
    private final HoneycombRateLimiterProperties props;
    private final Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    public RateLimitFilter(HoneycombRateLimiterProperties props) {
        this.props = props;
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        if (!props.isEnabled()) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!(path.startsWith("/honeycomb") || path.startsWith("/cells"))) {
            return chain.filter(exchange);
        }
        String cell = CellPathResolver.resolveCell(path);
        if (cell == null || cell.isBlank()) {
            return chain.filter(exchange);
        }
        String key = cell;
        RateLimiter limiter = limiters.computeIfAbsent(key, k -> buildLimiter(cell));
        return chain.filter(exchange)
                .transformDeferred(RateLimiterOperator.of(limiter))
                .onErrorResume(ex -> {
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    return exchange.getResponse().setComplete();
                });
    }

    private RateLimiter buildLimiter(String cell) {
        HoneycombRateLimiterProperties.RateLimitConfig cfg = props.resolve(cell);
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(cfg.getLimitForPeriod())
                .limitRefreshPeriod(cfg.getRefreshPeriod() == null ? Duration.ofSeconds(1) : cfg.getRefreshPeriod())
                .timeoutDuration(cfg.getTimeout() == null ? Duration.ZERO : cfg.getTimeout())
                .build();
        return RateLimiter.of("cell-" + (cell == null ? "global" : cell), config);
    }
}
