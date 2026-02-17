package com.honeycomb.core.web;

import com.honeycomb.core.config.HoneycombApiVersionProperties;
import com.honeycomb.core.util.HoneycombConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that enables API versioning by rewriting versioned path prefixes.
 *
 * <p>When API versioning is enabled via {@code honeycomb.api.versioning-enabled=true},
 * requests to {@code /v1/honeycomb/...} are transparently rewritten to
 * {@code /honeycomb/...} before reaching controllers. The response includes
 * an {@code X-API-Version} header.</p>
 *
 * <p>This approach keeps controllers unmodified while supporting both
 * versioned and legacy (un-versioned) paths simultaneously.</p>
 *
 * @since 1.5.0
 * @see HoneycombApiVersionProperties
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class ApiVersionFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiVersionFilter.class);

    private final HoneycombApiVersionProperties versionProperties;

    public ApiVersionFilter(HoneycombApiVersionProperties versionProperties) {
        this.versionProperties = versionProperties;
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        if (!versionProperties.isVersioningEnabled()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        String versionPrefix = versionProperties.getVersionPrefix();

        // Add API version header to all honeycomb responses
        if (path.startsWith(HoneycombConstants.Paths.HONEYCOMB_BASE) ||
                path.startsWith(versionPrefix + HoneycombConstants.Paths.HONEYCOMB_BASE)) {
            exchange.getResponse().getHeaders()
                    .add(versionProperties.getVersionHeader(), versionProperties.getCurrentVersion());
        }

        // Rewrite /v1/honeycomb/... → /honeycomb/...
        String versionedHoneycombPrefix = versionPrefix + HoneycombConstants.Paths.HONEYCOMB_BASE;
        if (path.startsWith(versionedHoneycombPrefix)) {
            String rewrittenPath = path.substring(versionPrefix.length());
            log.debug("API version rewrite: {} → {}", path, rewrittenPath);

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .path(rewrittenPath)
                    .build();
            ServerWebExchange mutatedExchange = exchange.mutate()
                    .request(mutatedRequest)
                    .build();
            return chain.filter(mutatedExchange);
        }

        return chain.filter(exchange);
    }
}
