package com.example.honeycomb.security;

import com.example.honeycomb.config.HoneycombSecurityProperties;
import com.example.honeycomb.web.CellPathResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.lang.NonNull;
import com.example.honeycomb.util.HoneycombConstants;
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
        if (isOpenApiPath(path)) {
            return chain.filter(exchange);
        }
        if (!path.startsWith(HoneycombConstants.Paths.HONEYCOMB_BASE)) {
            return chain.filter(exchange);
        }
        String authorization = exchange.getRequest().getHeaders().getFirst(HoneycombConstants.Headers.AUTHORIZATION);
        if (authorization != null && !authorization.isBlank()) {
            return chain.filter(exchange);
        }
        String header = Objects.requireNonNullElse(apiKeys.getHeader(), HoneycombConstants.Headers.API_KEY);
        String key = exchange.getRequest().getHeaders().getFirst(header);
        if (key == null || key.isBlank()) {
            return unauthorized(exchange, HoneycombConstants.ErrorKeys.MISSING_API_KEY);
        }
        if (!apiKeys.isKnownKey(key)) {
            return unauthorized(exchange, HoneycombConstants.ErrorKeys.INVALID_API_KEY);
        }
        String cell = CellPathResolver.resolveCell(path);
        if (cell == null || cell.isBlank()) {
            return chain.filter(exchange);
        }
        var allowed = apiKeys.resolveAllowedKeys(cell);
        if (!allowed.isEmpty() && allowed.stream().noneMatch(k -> k.equals(key))) {
            return unauthorized(exchange, HoneycombConstants.ErrorKeys.CELL_ACCESS_DENIED);
        }
        var auth = new UsernamePasswordAuthenticationToken("api-key", key, java.util.List.of(new SimpleGrantedAuthority(HoneycombConstants.Roles.API_KEY)));
        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(new SecurityContextImpl(auth))));
    }

    private boolean isOpenApiPath(String path) {
        if (path == null) return false;
        return path.startsWith(HoneycombConstants.Paths.HONEYCOMB_SWAGGER_UI)
            || path.startsWith(HoneycombConstants.Paths.HONEYCOMB_API_DOCS)
            || path.startsWith(HoneycombConstants.Paths.HONEYCOMB_SWAGGER);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"" + HoneycombConstants.JsonKeys.ERROR + "\":\"" + message + "\"}").getBytes();
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
