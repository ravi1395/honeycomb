package com.honeycomb.grpc.client;

import com.honeycomb.grpc.config.HoneycombGrpcProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GrpcSharedwallClient} builder and factory methods.
 *
 * @since 1.4.0
 */
class GrpcSharedwallClientTest {

    @Test
    @DisplayName("builder produces a non-null client")
    void builderProducesClient() {
        var client = GrpcSharedwallClient.builder()
                .target("localhost:9090")
                .fromCell("TestCell")
                .deadline(Duration.ofSeconds(5))
                .negotiationType("plaintext")
                .loadBalancingPolicy("round_robin")
                .build();

        assertNotNull(client);
        // Clean up
        client.shutdown();
    }

    @Test
    @DisplayName("builder uses default values when not set")
    void builderDefaults() {
        var client = GrpcSharedwallClient.builder().build();
        assertNotNull(client);
        client.shutdown();
    }

    @Test
    @DisplayName("builder fromProperties configures correctly")
    void builderFromProperties() {
        var props = new HoneycombGrpcProperties();
        props.getClient().setDefaultTarget("order-service:9090");
        props.getClient().setDeadline(Duration.ofSeconds(15));

        var client = GrpcSharedwallClient.builder()
                .fromProperties(props, "OrderCell")
                .fromCell("CallerCell")
                .build();

        assertNotNull(client);
        client.shutdown();
    }

    @Test
    @DisplayName("builder fromProperties uses per-cell target when available")
    void builderFromProperties_perCellTarget() {
        var props = new HoneycombGrpcProperties();
        props.getClient().setDefaultTarget("default:9090");
        props.getClient().getPerCellTargets().put("InventoryCell", "inventory:9090");

        var client = GrpcSharedwallClient.builder()
                .fromProperties(props, "InventoryCell")
                .build();

        assertNotNull(client);
        client.shutdown();
    }

    @Test
    @DisplayName("shutdown is idempotent")
    void shutdownIsSafe() {
        var client = GrpcSharedwallClient.builder()
                .target("localhost:19090")
                .build();

        assertDoesNotThrow(() -> {
            client.shutdown();
            client.shutdown(); // double shutdown should be safe
        });
    }
}
