package com.honeycomb.core.web;

import com.honeycomb.core.config.HoneycombAuditProperties;
import com.honeycomb.core.service.AuditLogService;
import com.honeycomb.core.service.CellServerManager;
import com.honeycomb.core.util.HoneycombConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Set;

/**
 * HTTP-level audit logging WebFilter for all Honeycomb requests.
 *
 * <p>Records method, path, status code, latency, tenant ID, and request ID
 * for every Honeycomb endpoint request. Sensitive headers (Authorization,
 * X-API-Key) are redacted to prevent PII leakage.</p>
 *
 * <p>Also tracks in-flight requests for graceful shutdown support via
 * {@link CellServerManager#trackRequestStart()} and
 * {@link CellServerManager#trackRequestEnd()}.</p>
 *
 * @since 1.5.0
 * @see HoneycombAuditProperties
 * @see AuditLogService
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 6)
public class HttpAuditFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpAuditFilter.class);

    /** Headers whose values should be redacted in audit logs. */
    private static final Set<String> REDACTED_HEADERS = Set.of(
            "authorization", "x-api-key", "cookie", "set-cookie"
    );

    private final HoneycombAuditProperties auditProperties;
    private final CellServerManager cellServerManager;

    public HttpAuditFilter(HoneycombAuditProperties auditProperties,
                           CellServerManager cellServerManager) {
        this.auditProperties = auditProperties;
        this.cellServerManager = cellServerManager;
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();

        // Only audit honeycomb endpoints
        if (!path.startsWith(HoneycombConstants.Paths.HONEYCOMB_BASE)) {
            return chain.filter(exchange);
        }

        // Track in-flight requests for graceful shutdown
        cellServerManager.trackRequestStart();

        String method = exchange.getRequest().getMethod().name();
        String requestId = exchange.getRequest().getHeaders()
                .getFirst(HoneycombConstants.Headers.REQUEST_ID);
        String tenantId = exchange.getRequest().getHeaders()
                .getFirst(HoneycombConstants.Headers.TENANT_ID);
        long startTime = System.currentTimeMillis();

        return chain.filter(exchange)
                .doFinally(signal -> {
                    cellServerManager.trackRequestEnd();

                    long latencyMs = System.currentTimeMillis() - startTime;
                    HttpStatus status = (HttpStatus) exchange.getResponse().getStatusCode();
                    int statusCode = status != null ? status.value() : 0;

                    if (auditProperties != null && auditProperties.getMaxEntries() > 0) {
                        log.info("AUDIT method={} path={} status={} latencyMs={} requestId={} tenant={} signal={}",
                                method, path, statusCode, latencyMs,
                                requestId != null ? requestId : "-",
                                tenantId != null ? tenantId : "-",
                                signal);
                    }
                });
    }

    /**
     * Redacts sensitive header values for audit logging.
     *
     * @param headerName the header name
     * @param headerValue the header value
     * @return the value or "[REDACTED]" if sensitive
     */
    public static String redactHeader(String headerName, String headerValue) {
        if (headerName == null || headerValue == null) return headerValue;
        return REDACTED_HEADERS.contains(headerName.toLowerCase()) ? "[REDACTED]" : headerValue;
    }
}
