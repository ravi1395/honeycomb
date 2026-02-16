package com.honeycomb.grpc.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.honeycomb.core.service.CellDataStore;
import com.honeycomb.grpc.proto.*;
import com.honeycomb.grpc.util.ProtoJsonConverter;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * gRPC service implementation for cell item CRUD operations.
 *
 * <p>Mirrors the HTTP {@code /honeycomb/cells/{cellName}/items} endpoints,
 * delegating to the same {@link CellDataStore} used by the HTTP controllers.
 * The injected {@code CellDataStore} is typically a {@code CellDataStoreRouter}
 * that resolves the correct backend (in-memory, Redis, Hibernate) per cell.</p>
 *
 * @since 1.4.0
 */
@GrpcService
public class CellGrpcService extends HoneycombCellServiceGrpc.HoneycombCellServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(CellGrpcService.class);

    private final CellDataStore dataStore;
    private final ObjectMapper objectMapper;

    public CellGrpcService(CellDataStore dataStore, ObjectMapper objectMapper) {
        this.dataStore = dataStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public void listItems(CellItemRequest request, StreamObserver<CellItemListResponse> responseObserver) {
        String cellName = request.getCellName();
        log.debug("gRPC ListItems: cell={}", cellName);

        try {
            var items = dataStore.list(cellName).collectList().block();

            CellItemListResponse.Builder response = CellItemListResponse.newBuilder();
            if (items != null) {
                for (var item : items) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = item instanceof Map ? (Map<String, Object>) item
                            : objectMapper.convertValue(item, Map.class);
                    String itemId = data.getOrDefault("id", "").toString();
                    response.addItems(CellItemResponse.newBuilder()
                            .setItemId(itemId)
                            .setCellName(cellName)
                            .setData(ProtoJsonConverter.mapToStruct(data))
                            .build());
                }
            }
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC ListItems failed for cell={}: {}", cellName, e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getItem(CellItemByIdRequest request, StreamObserver<CellItemResponse> responseObserver) {
        String cellName = request.getCellName();
        String itemId = request.getItemId();
        log.debug("gRPC GetItem: cell={}, id={}", cellName, itemId);

        try {
            Object item = dataStore.get(cellName, itemId).block();

            if (item == null) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Item not found: " + itemId)
                        .asRuntimeException());
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = item instanceof Map ? (Map<String, Object>) item
                    : objectMapper.convertValue(item, Map.class);

            responseObserver.onNext(CellItemResponse.newBuilder()
                    .setItemId(itemId)
                    .setCellName(cellName)
                    .setData(ProtoJsonConverter.mapToStruct(data))
                    .build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC GetItem failed: {}", e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void createItem(CellItemCreateRequest request, StreamObserver<CellItemResponse> responseObserver) {
        String cellName = request.getCellName();
        log.debug("gRPC CreateItem: cell={}", cellName);

        try {
            Map<String, Object> payload = resolvePayload(request.getPayload(), request.getRawJsonPayload());

            Object created = dataStore.create(cellName, payload).block();

            @SuppressWarnings("unchecked")
            Map<String, Object> data = created instanceof Map ? (Map<String, Object>) created
                    : objectMapper.convertValue(created, Map.class);
            String itemId = data.getOrDefault("id", "").toString();

            responseObserver.onNext(CellItemResponse.newBuilder()
                    .setItemId(itemId)
                    .setCellName(cellName)
                    .setData(ProtoJsonConverter.mapToStruct(data))
                    .build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC CreateItem failed: {}", e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void updateItem(CellItemUpdateRequest request, StreamObserver<CellItemResponse> responseObserver) {
        String cellName = request.getCellName();
        String itemId = request.getItemId();
        log.debug("gRPC UpdateItem: cell={}, id={}", cellName, itemId);

        try {
            Map<String, Object> payload = resolvePayload(request.getPayload(), request.getRawJsonPayload());

            Object updated = dataStore.update(cellName, itemId, payload).block();

            @SuppressWarnings("unchecked")
            Map<String, Object> data = updated instanceof Map ? (Map<String, Object>) updated
                    : objectMapper.convertValue(updated, Map.class);

            responseObserver.onNext(CellItemResponse.newBuilder()
                    .setItemId(itemId)
                    .setCellName(cellName)
                    .setData(ProtoJsonConverter.mapToStruct(data))
                    .build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC UpdateItem failed: {}", e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void deleteItem(CellItemByIdRequest request, StreamObserver<Empty> responseObserver) {
        String cellName = request.getCellName();
        String itemId = request.getItemId();
        log.debug("gRPC DeleteItem: cell={}, id={}", cellName, itemId);

        try {
            dataStore.delete(cellName, itemId).block();

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC DeleteItem failed: {}", e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private Map<String, Object> resolvePayload(Struct struct, String rawJson) {
        if (rawJson != null && !rawJson.isBlank()) {
            return ProtoJsonConverter.structToMap(ProtoJsonConverter.jsonToStruct(rawJson, objectMapper));
        }
        if (struct != null && struct.getFieldsCount() > 0) {
            return ProtoJsonConverter.structToMap(struct);
        }
        return Map.of();
    }
}
