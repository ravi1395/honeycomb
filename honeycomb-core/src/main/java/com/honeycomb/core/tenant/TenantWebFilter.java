package com.honeycomb.core.tenant;

import com.honeycomb.core.config.HoneycombTenantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * {@link WebFilter} that extracts the tenant identifier from the
 * configured HTTP header and places it into the Reactor context.
 *
 * <p>Runs at {@link Ordered#HIGHEST_PRECEDENCE} + 1 — immediately after
 * the request-ID filter. When enforcement is enabled and the header is
 * missing (and no default tenant is configured), the request is rejected
 * with {@code 400 Bad Request}.</p>
 *
 * @since 1.4.3
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@ConditionalOnProperty(name = "honeycomb.tenant.enabled", havingValue = "true")
public class TenantWebFilter implements WebFilter {
    private static final Logger log = LoggerFactory.getLogger(TenantWebFilter.class);

    private final HoneycombTenantProperties props;

    public TenantWebFilter(HoneycombTenantProperties props) {
        this.props = props;
    }

    @Override
    @NonNull
    @SuppressWarnings("null")
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String tenantId = exchange.getRequest().getHeaders().getFirst(props.getHeaderName());

        // fallback to default
        if (!StringUtils.hasText(tenantId)) {
            tenantId = StringUtils.hasText(props.getDefaultTenant()) ? props.getDefaultTenant() : null;
        }

        // enforce
        if (tenantId == null && props.isEnforceHeader()) {
            String path = exchange.getRequest().getPath().pathWithinApplication().value();
            // allow actuator and swagger through without tenant
            if (path.startsWith("/actuator") || path.startsWith("/swagger") || path.startsWith("/v3/api-docs")) {
                return chain.filter(exchange);
            }
            log.warn("Missing tenant header '{}' on {}", props.getHeaderName(), path);
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().setComplete();
        }

        // validate against allowed list
        if (tenantId != null && !props.getAllowedTenants().isEmpty()) {
            List<String> allowed = props.getAllowedTenants();
            if (!allowed.contains(tenantId)) {
                log.warn("Tenant '{}' not in allowed list", tenantId);
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
        }

        final String resolvedTenant = tenantId;

        // propagate tenant in response header and Reactor context
        if (resolvedTenant != null) {
            exchange.getResponse().beforeCommit(() -> {
                exchange.getResponse().getHeaders().set(props.getHeaderName(), resolvedTenant);
                return Mono.empty();
            });
        }

        return chain.filter(exchange)
                .contextWrite(ctx -> resolvedTenant != null
                        ? TenantContext.withTenant(ctx, resolvedTenant)
                        : ctx);
    }
}
