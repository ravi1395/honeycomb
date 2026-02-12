package com.example.honeycomb.security;

import com.example.honeycomb.config.HoneycombSecurityProperties;
import com.example.honeycomb.web.CellPathResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import com.example.honeycomb.util.HoneycombConstants;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@SuppressWarnings("null")
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class JwtCellAccessFilter implements WebFilter {
    private final HoneycombSecurityProperties securityProperties;

    public JwtCellAccessFilter(HoneycombSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        var jwt = securityProperties.getJwt();
        if (jwt == null || !jwt.isEnabled()) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (path.startsWith(HoneycombConstants.Paths.HONEYCOMB_ACTUATOR)) {
            return chain.filter(exchange);
        }
        if (path.startsWith(HoneycombConstants.Paths.HONEYCOMB_SHARED)) {
            String shared = resolveSharedMethod(path);
            List<String> required = jwt.resolveSharedMethodRoles(shared);
            if (required == null || required.isEmpty()) {
                return chain.filter(exchange);
            }
            return ReactiveSecurityContextHolder.getContext()
                    .map(ctx -> ctx.getAuthentication())
                    .defaultIfEmpty(null)
                    .flatMap(auth -> authorize(auth, required, exchange, chain));
        }
        String cell = CellPathResolver.resolveCell(path);
        if (cell == null || cell.isBlank()) {
            return chain.filter(exchange);
        }
        String operation = resolveOperation(exchange, path);
        List<String> required = jwt.resolveRequiredRoles(cell, operation);
        if (required == null || required.isEmpty()) {
            return chain.filter(exchange);
        }
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .defaultIfEmpty(null)
                .flatMap(auth -> authorize(auth, required, exchange, chain));
    }

    private Mono<Void> authorize(Authentication auth, List<String> required, ServerWebExchange exchange, WebFilterChain chain) {
        if (auth == null || !auth.isAuthenticated()) {
            return chain.filter(exchange);
        }
        Set<String> roles = auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toSet());
        boolean allowed = required.stream().anyMatch(roles::contains);
        if (allowed) {
            return chain.filter(exchange);
        }
        return forbidden(exchange, HoneycombConstants.ErrorKeys.INSUFFICIENT_ROLES);
    }

    private String resolveOperation(ServerWebExchange exchange, String path) {
        String method = exchange.getRequest().getMethod() != null
            ? exchange.getRequest().getMethod().name()
            : null;
        if (path.contains("/" + HoneycombConstants.Paths.ITEMS)) {
            if ("POST".equalsIgnoreCase(method)) return HoneycombConstants.Ops.CREATE;
            if ("GET".equalsIgnoreCase(method)) return HoneycombConstants.Ops.READ;
            if ("PUT".equalsIgnoreCase(method)) return HoneycombConstants.Ops.UPDATE;
            if ("DELETE".equalsIgnoreCase(method)) return HoneycombConstants.Ops.DELETE;
        }
        if (path.contains("/" + HoneycombConstants.Paths.SHARED)) {
            return HoneycombConstants.Ops.SHARED;
        }
        return method == null ? HoneycombConstants.Ops.UNKNOWN : method.toLowerCase();
    }

    private String resolveSharedMethod(String path) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (HoneycombConstants.Paths.SHARED.equals(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return null;
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"" + HoneycombConstants.JsonKeys.ERROR + "\":\"" + message + "\"}").getBytes();
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
