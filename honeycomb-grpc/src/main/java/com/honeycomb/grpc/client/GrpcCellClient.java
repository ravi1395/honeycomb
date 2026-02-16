package com.honeycomb.grpc.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Struct;
import com.honeycomb.grpc.config.HoneycombGrpcProperties;
import com.honeycomb.grpc.proto.*;
import com.honeycomb.grpc.util.ProtoJsonConverter;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * gRPC-based client for cell CRUD operations on remote Honeycomb instances.
 *
 * <p>Provides the same semantics as the HTTP {@code /honeycomb/cells/{name}/items}
 * endpoints but over gRPC. Useful for high-throughput inter-cell data access
 * where Protobuf serialization is preferred over JSON/HTTP.</p>
 *
 * @since 1.4.0
 */
public final class GrpcCellClient {

    private static final Logger log = LoggerFactory.getLogger(GrpcCellClient.class);

    private final ManagedChannel channel;
    private final HoneycombCellServiceGrpc.HoneycombCellServiceBlockingStub stub;
    private final Duration deadline;
    private final ObjectMapper objectMapper;

    private GrpcCellClient(String target, Duration deadline, ObjectMapper objectMapper, String negotiationType) {
        this.deadline = deadline;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();

        ManagedChannelBuilder<?> cb = ManagedChannelBuilder.forTarget(target);
        if ("plaintext".equals(negotiationType)) {
            cb.usePlaintext();
        }
        this.channel = cb.build();
        this.stub = HoneycombCellServiceGrpc.newBlockingStub(channel);
    }

    public static GrpcCellClient create(String target) {
        return new GrpcCellClient(target, Duration.ofSeconds(10), null, "plaintext");
    }

    public static GrpcCellClient create(HoneycombGrpcProperties props, String cellName) {
        var client = props.getClient();
        String target = client.getPerCellTargets().getOrDefault(cellName, client.getDefaultTarget());
        return new GrpcCellClient(target, client.getDeadline(), null, client.getNegotiationType());
    }

    /**
     * List all items in a cell.
     */
    public Mono<List<Map<String, Object>>> listItems(String cellName) {
        return Mono.fromCallable(() -> {
            var resp = stub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS)
                    .listItems(CellItemRequest.newBuilder().setCellName(cellName).build());
            return resp.getItemsList().stream()
                    .map(item -> ProtoJsonConverter.structToMap(item.getData()))
                    .collect(Collectors.toList());
        });
    }

    /**
     * Get a single item by ID.
     */
    public Mono<Map<String, Object>> getItem(String cellName, String itemId) {
        return Mono.fromCallable(() -> {
            var resp = stub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS)
                    .getItem(CellItemByIdRequest.newBuilder()
                            .setCellName(cellName)
                            .setItemId(itemId)
                            .build());
            return ProtoJsonConverter.structToMap(resp.getData());
        });
    }

    /**
     * Create a new item.
     */
    public Mono<Map<String, Object>> createItem(String cellName, Map<String, Object> payload) {
        return Mono.fromCallable(() -> {
            var resp = stub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS)
                    .createItem(CellItemCreateRequest.newBuilder()
                            .setCellName(cellName)
                            .setPayload(ProtoJsonConverter.mapToStruct(payload))
                            .build());
            return ProtoJsonConverter.structToMap(resp.getData());
        });
    }

    /**
     * Update an existing item.
     */
    public Mono<Map<String, Object>> updateItem(String cellName, String itemId, Map<String, Object> payload) {
        return Mono.fromCallable(() -> {
            var resp = stub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS)
                    .updateItem(CellItemUpdateRequest.newBuilder()
                            .setCellName(cellName)
                            .setItemId(itemId)
                            .setPayload(ProtoJsonConverter.mapToStruct(payload))
                            .build());
            return ProtoJsonConverter.structToMap(resp.getData());
        });
    }

    /**
     * Delete an item by ID.
     */
    public Mono<Void> deleteItem(String cellName, String itemId) {
        return Mono.fromRunnable(() ->
                stub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS)
                        .deleteItem(CellItemByIdRequest.newBuilder()
                                .setCellName(cellName)
                                .setItemId(itemId)
                                .build()));
    }

    /**
     * Gracefully shut down the channel.
     */
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
    }
}
