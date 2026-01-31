package com.example.honeycomb.security;

import com.example.honeycomb.config.HoneycombSecurityProperties;
import com.example.honeycomb.web.CellPathResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
@SuppressWarnings("null")
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiKeyAuthFilter implements WebFilter {
    private final HoneycombSecurityProperties securityProperties;

    public ApiKeyAuthFilter(HoneycombSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        var apiKeys = securityProperties.getApiKeys();
        if (!apiKeys.isEnabled()) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!path.startsWith("/honeycomb")) {
            return chain.filter(exchange);
        }
        String authorization = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authorization != null && !authorization.isBlank()) {
            return chain.filter(exchange);
        }
        String header = Objects.requireNonNullElse(apiKeys.getHeader(), "X-API-Key");
        String key = exchange.getRequest().getHeaders().getFirst(header);
        if (key == null || key.isBlank()) {
            return unauthorized(exchange, "missing-api-key");
        }
        if (!apiKeys.isKnownKey(key)) {
            return unauthorized(exchange, "invalid-api-key");
        }
        String cell = CellPathResolver.resolveCell(path);
        if (cell == null || cell.isBlank()) {
            return chain.filter(exchange);
        }
        var allowed = apiKeys.resolveAllowedKeys(cell);
        if (!allowed.isEmpty() && allowed.stream().noneMatch(k -> k.equals(key))) {
            return unauthorized(exchange, "cell-access-denied");
        }
        return chain.filter(exchange);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"error\":\"" + message + "\"}").getBytes();
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
