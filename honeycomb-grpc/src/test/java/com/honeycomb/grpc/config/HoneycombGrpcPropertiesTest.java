package com.honeycomb.grpc.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HoneycombGrpcProperties} defaults and builder accessors.
 *
 * @since 1.4.0
 */
class HoneycombGrpcPropertiesTest {

    @Test
    @DisplayName("defaults are sane")
    void defaults() {
        var props = new HoneycombGrpcProperties();

        assertFalse(props.isEnabled());
        assertEquals(HoneycombGrpcProperties.TransportMode.BOTH, props.getTransport());
        assertTrue(props.isReflectionEnabled());
        assertTrue(props.isHealthEnabled());
    }

    @Test
    @DisplayName("server defaults")
    void serverDefaults() {
        var server = new HoneycombGrpcProperties().getServer();

        assertEquals(9090, server.getPort());
        assertEquals(4 * 1024 * 1024, server.getMaxInboundMessageSize());
        assertEquals(Duration.ofMinutes(5), server.getKeepAliveTime());
        assertEquals(Duration.ofSeconds(20), server.getKeepAliveTimeout());
    }

    @Test
    @DisplayName("client defaults")
    void clientDefaults() {
        var client = new HoneycombGrpcProperties().getClient();

        assertEquals("localhost:9090", client.getDefaultTarget());
        assertEquals(Duration.ofSeconds(10), client.getDeadline());
        assertEquals("plaintext", client.getNegotiationType());
        assertEquals("round_robin", client.getLoadBalancingPolicy());
        assertEquals(1, client.getMaxRetries());
        assertNotNull(client.getPerCellTargets());
        assertTrue(client.getPerCellTargets().isEmpty());
    }

    @Test
    @DisplayName("TLS defaults are disabled")
    void tlsDefaults() {
        var serverTls = new HoneycombGrpcProperties().getServer().getTls();
        var clientTls = new HoneycombGrpcProperties().getClient().getTls();

        assertFalse(serverTls.isEnabled());
        assertFalse(clientTls.isEnabled());
    }

    @Test
    @DisplayName("transport mode enum values")
    void transportModes() {
        assertEquals(3, HoneycombGrpcProperties.TransportMode.values().length);
        assertNotNull(HoneycombGrpcProperties.TransportMode.valueOf("HTTP"));
        assertNotNull(HoneycombGrpcProperties.TransportMode.valueOf("GRPC"));
        assertNotNull(HoneycombGrpcProperties.TransportMode.valueOf("BOTH"));
    }

    @Test
    @DisplayName("setters update values")
    void setters() {
        var props = new HoneycombGrpcProperties();
        props.setEnabled(true);
        props.setTransport(HoneycombGrpcProperties.TransportMode.GRPC);

        assertTrue(props.isEnabled());
        assertEquals(HoneycombGrpcProperties.TransportMode.GRPC, props.getTransport());
    }
}
