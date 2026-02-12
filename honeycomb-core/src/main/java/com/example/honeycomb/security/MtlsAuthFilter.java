package com.example.honeycomb.security;

import com.example.honeycomb.config.HoneycombSecurityProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import com.example.honeycomb.util.HoneycombConstants;

import java.security.cert.X509Certificate;
import java.util.Arrays;

@Component
@SuppressWarnings("null")
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class MtlsAuthFilter implements WebFilter {
    private final HoneycombSecurityProperties securityProperties;

    public MtlsAuthFilter(HoneycombSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        var mtls = securityProperties.getMtls();
        if (mtls == null || !mtls.isEnabled()) {
            return chain.filter(exchange);
        }
        var sslInfo = exchange.getRequest().getSslInfo();
        if (sslInfo == null || sslInfo.getPeerCertificates() == null || sslInfo.getPeerCertificates().length == 0) {
            if (mtls.isRequireClientCert()) {
                return unauthorized(exchange, HoneycombConstants.ErrorKeys.MISSING_CLIENT_CERT);
            }
            return chain.filter(exchange);
        }
        X509Certificate[] certs = sslInfo.getPeerCertificates();
        if (mtls.getAllowedSubjects() != null && !mtls.getAllowedSubjects().isEmpty()) {
            boolean allowed = Arrays.stream(certs)
                    .map(c -> c.getSubjectX500Principal().getName())
                    .anyMatch(subject -> mtls.getAllowedSubjects().stream().anyMatch(subject::equals));
            if (!allowed) {
                return unauthorized(exchange, HoneycombConstants.ErrorKeys.CLIENT_CERT_NOT_ALLOWED);
            }
        }
        return chain.filter(exchange);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"" + HoneycombConstants.JsonKeys.ERROR + "\":\"" + message + "\"}").getBytes();
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
