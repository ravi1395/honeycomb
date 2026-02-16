package com.honeycomb.grpc.client;

import io.grpc.ClientInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HoneycombGrpcClientInterceptor}.
 *
 * @since 1.4.0
 */
class HoneycombGrpcClientInterceptorTest {

    @Test
    @DisplayName("interceptor accepts null fromCell")
    void nullFromCell() {
        var interceptor = new HoneycombGrpcClientInterceptor(null);
        assertNotNull(interceptor);
        assertInstanceOf(ClientInterceptor.class, interceptor);
    }

    @Test
    @DisplayName("interceptor accepts non-null fromCell")
    void nonNullFromCell() {
        var interceptor = new HoneycombGrpcClientInterceptor("TestCell");
        assertNotNull(interceptor);
    }
}
