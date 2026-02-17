package com.honeycomb.core.web;

import com.honeycomb.core.config.HoneycombRateLimiterProperties;
import com.honeycomb.core.util.HoneycombConstants;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
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

/**
 * {@link WebFilter} that applies per-IP and per-tenant rate limiting on Honeycomb
 * endpoints using Resilience4j rate limiters.
 *
 * <p>Runs at high priority (after mTLS and API-key filters). When a client
 * exceeds the configured limit, responds with HTTP 429 Too Many Requests.</p>
 *
 * <p><b>v1.5.0:</b> Added per-tenant rate limiting. When
 * {@code honeycomb.rate-limiter.tenant-aware=true}, the rate limiter key includes
 * the tenant ID from the {@code X-Tenant-Id} header, enabling independent rate
 * limits per tenant.</p>
 *
 * @see com.honeycomb.core.config.HoneycombRateLimiterProperties
 */
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
        if (!(path.startsWith(HoneycombConstants.Paths.HONEYCOMB_BASE)
            || path.startsWith(HoneycombConstants.Paths.CELLS_BASE))) {
            return chain.filter(exchange);
        }
        String cell = CellPathResolver.resolveCell(path);
        if (cell == null || cell.isBlank()) {
            return chain.filter(exchange);
        }

        // v1.5.0: tenant-aware rate limiting
        String tenantId = exchange.getRequest().getHeaders()
                .getFirst(HoneycombConstants.Headers.TENANT_ID);
        String limiterKey = buildLimiterKey(cell, tenantId);

        RateLimiter limiter = limiters.computeIfAbsent(limiterKey, k -> buildLimiter(cell, tenantId));
        return chain.filter(exchange)
                .transformDeferred(RateLimiterOperator.of(limiter))
                .onErrorResume(RequestNotPermitted.class, ex -> {
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    return exchange.getResponse().setComplete();
                });
    }

    private String buildLimiterKey(String cell, String tenantId) {
        String base = HoneycombConstants.Names.LIMITER_CELL_PREFIX
                + (cell == null ? HoneycombConstants.Names.LIMITER_GLOBAL : cell);
        if (props.isTenantAware() && tenantId != null && !tenantId.isBlank()) {
            return base + ":tenant:" + tenantId;
        }
        return base;
    }

    private RateLimiter buildLimiter(String cell, String tenantId) {
        HoneycombRateLimiterProperties.RateLimitConfig cfg = props.resolveForTenant(tenantId, cell);
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(cfg.getLimitForPeriod())
                .limitRefreshPeriod(cfg.getRefreshPeriod() == null ? Duration.ofSeconds(1) : cfg.getRefreshPeriod())
                .timeoutDuration(cfg.getTimeout() == null ? Duration.ZERO : cfg.getTimeout())
                .build();
        return RateLimiter.of(buildLimiterKey(cell, tenantId), config);
    }
}
