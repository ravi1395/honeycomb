package com.honeycomb.core.tenant;

import com.honeycomb.core.config.HoneycombTenantProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TenantWebFilter}.
 *
 * @since 1.4.3
 */
class TenantWebFilterTest {

    private HoneycombTenantProperties props;

    @BeforeEach
    void setUp() {
        props = new HoneycombTenantProperties();
        props.setEnabled(true);
        props.setHeaderName("X-Tenant-Id");
        props.setEnforceHeader(true);
    }

    @Test
    @DisplayName("passes tenant from header into Reactor context")
    void tenantFromHeader() {
        TenantWebFilter filter = new TenantWebFilter(props);
        MockServerHttpRequest request = MockServerHttpRequest.get("/honeycomb/cells")
                .header("X-Tenant-Id", "acme")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<Context> captured = new AtomicReference<>();
        WebFilterChain chain = ex -> Mono.deferContextual(ctx -> {
            captured.set(Context.of(ctx));
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertNotNull(captured.get());
        assertTrue(TenantContext.fromContext(captured.get()).isPresent());
        assertEquals("acme", TenantContext.fromContext(captured.get()).get());
    }

    @Test
    @DisplayName("falls back to default tenant when header absent")
    void fallbackDefaultTenant() {
        props.setDefaultTenant("default-tenant");
        TenantWebFilter filter = new TenantWebFilter(props);
        MockServerHttpRequest request = MockServerHttpRequest.get("/honeycomb/cells").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<Context> captured = new AtomicReference<>();
        WebFilterChain chain = ex -> Mono.deferContextual(ctx -> {
            captured.set(Context.of(ctx));
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals("default-tenant", TenantContext.fromContext(captured.get()).get());
    }

    @Test
    @DisplayName("returns 400 when header missing and enforcement is on")
    void rejectMissingTenantHeader() {
        props.setEnforceHeader(true);
        TenantWebFilter filter = new TenantWebFilter(props);
        MockServerHttpRequest request = MockServerHttpRequest.get("/honeycomb/cells").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("allows actuator paths without tenant header")
    void actuatorPathBypassed() {
        TenantWebFilter filter = new TenantWebFilter(props);
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<Boolean> chainCalled = new AtomicReference<>(false);
        WebFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertTrue(chainCalled.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("returns 403 when tenant not in allowed list")
    void rejectDisallowedTenant() {
        props.setAllowedTenants(List.of("alpha", "beta"));
        TenantWebFilter filter = new TenantWebFilter(props);
        MockServerHttpRequest request = MockServerHttpRequest.get("/honeycomb/cells")
                .header("X-Tenant-Id", "rogue")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("accepts any tenant when allowedTenants list is empty")
    void acceptAnyTenantWhenNoAllowList() {
        props.setAllowedTenants(List.of()); // empty = accept any
        TenantWebFilter filter = new TenantWebFilter(props);
        MockServerHttpRequest request = MockServerHttpRequest.get("/honeycomb/cells")
                .header("X-Tenant-Id", "anything")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<Context> captured = new AtomicReference<>();
        WebFilterChain chain = ex -> Mono.deferContextual(ctx -> {
            captured.set(Context.of(ctx));
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals("anything", TenantContext.fromContext(captured.get()).get());
    }
}
