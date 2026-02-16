package com.honeycomb.grpc.client;

import com.honeycomb.grpc.config.HoneycombGrpcProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GrpcCellClient} factory and builder.
 *
 * @since 1.4.0
 */
class GrpcCellClientTest {

    @Test
    @DisplayName("create with target string")
    void createWithTarget() {
        var client = GrpcCellClient.create("localhost:9090");
        assertNotNull(client);
        client.shutdown();
    }

    @Test
    @DisplayName("create with properties")
    void createWithProperties() {
        var props = new HoneycombGrpcProperties();
        props.getClient().setDefaultTarget("catalog-service:9090");

        var client = GrpcCellClient.create(props, "CatalogCell");
        assertNotNull(client);
        client.shutdown();
    }

    @Test
    @DisplayName("create with properties uses per-cell target")
    void createWithProperties_perCellTarget() {
        var props = new HoneycombGrpcProperties();
        props.getClient().setDefaultTarget("default:9090");
        props.getClient().getPerCellTargets().put("OrderCell", "order-svc:9090");

        var client = GrpcCellClient.create(props, "OrderCell");
        assertNotNull(client);
        client.shutdown();
    }
}
