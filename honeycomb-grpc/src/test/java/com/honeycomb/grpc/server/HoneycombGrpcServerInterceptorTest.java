package com.honeycomb.grpc.server;

import io.grpc.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HoneycombGrpcServerInterceptor} — metadata extraction and MDC.
 *
 * @since 1.4.0
 */
class HoneycombGrpcServerInterceptorTest {

    private final HoneycombGrpcServerInterceptor interceptor = new HoneycombGrpcServerInterceptor();

    @Test
    @DisplayName("metadata keys are correctly defined")
    void metadataKeys() {
        assertEquals("x-request-id", HoneycombGrpcServerInterceptor.REQUEST_ID_KEY.name());
        assertEquals("x-from-cell", HoneycombGrpcServerInterceptor.FROM_CELL_KEY.name());
        assertEquals("x-api-key", HoneycombGrpcServerInterceptor.API_KEY_KEY.name());
        assertEquals("x-shared-version", HoneycombGrpcServerInterceptor.SHARED_VERSION_KEY.name());
        assertEquals("idempotency-key", HoneycombGrpcServerInterceptor.IDEMPOTENCY_KEY.name());
    }

    @Test
    @DisplayName("context keys are correctly defined")
    void contextKeys() {
        assertNotNull(HoneycombGrpcServerInterceptor.CTX_REQUEST_ID);
        assertNotNull(HoneycombGrpcServerInterceptor.CTX_FROM_CELL);
    }

    @Test
    @DisplayName("interceptor is a ServerInterceptor")
    void implementsInterface() {
        assertInstanceOf(ServerInterceptor.class, interceptor);
    }
}
