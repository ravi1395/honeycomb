package com.honeycomb.grpc.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Struct;
import com.honeycomb.core.dto.SharedwallInvokeInfo;
import com.honeycomb.grpc.config.HoneycombGrpcProperties;
import com.honeycomb.grpc.proto.*;
import com.honeycomb.grpc.util.ProtoJsonConverter;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * gRPC-based client for invoking {@code @Sharedwall} methods on remote cells.
 *
 * <p>This is the gRPC counterpart to {@code SharedwallClient} (HTTP). It can be
 * used as a drop-in replacement when the target cell exposes a gRPC endpoint.
 * Uses the same reactive {@link Mono}/{@link Flux} return types for seamless
 * integration with existing Honeycomb code.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * GrpcSharedwallClient client = GrpcSharedwallClient.builder()
 *         .target("order-service:9090")
 *         .fromCell("PaymentCell")
 *         .build();
 *
 * Mono<Map<String, Object>> result = client.invoke("discount", Map.of("amount", 100));
 * }</pre>
 *
 * @since 1.4.0
 * @see com.honeycomb.core.client.SharedwallClient
 */
public final class GrpcSharedwallClient {

    private static final Logger log = LoggerFactory.getLogger(GrpcSharedwallClient.class);

    private final ManagedChannel channel;
    private final HoneycombSharedwallServiceGrpc.HoneycombSharedwallServiceBlockingStub blockingStub;
    private final HoneycombCellServiceGrpc.HoneycombCellServiceBlockingStub cellBlockingStub;
    private final String fromCell;
    private final Duration deadline;
    private final ObjectMapper objectMapper;

    /** Channel cache: target → ManagedChannel. */
    private static final ConcurrentHashMap<String, ManagedChannel> CHANNEL_CACHE = new ConcurrentHashMap<>();

    private GrpcSharedwallClient(Builder builder) {
        this.fromCell = builder.fromCell;
        this.deadline = builder.deadline;
        this.objectMapper = builder.objectMapper == null
                ? new ObjectMapper().findAndRegisterModules()
                : builder.objectMapper;

        this.channel = CHANNEL_CACHE.computeIfAbsent(builder.target, target -> {
            log.info("Creating gRPC channel to target={}", target);
            ManagedChannelBuilder<?> cb = ManagedChannelBuilder.forTarget(target);
            if ("plaintext".equals(builder.negotiationType)) {
                cb.usePlaintext();
            }
            if (builder.loadBalancingPolicy != null) {
                cb.defaultLoadBalancingPolicy(builder.loadBalancingPolicy);
            }
            return cb.build();
        });

        Metadata defaultHeaders = new Metadata();
        if (fromCell != null && !fromCell.isBlank()) {
            defaultHeaders.put(Metadata.Key.of("x-from-cell", Metadata.ASCII_STRING_MARSHALLER), fromCell);
        }

        this.blockingStub = HoneycombSharedwallServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(defaultHeaders));
        this.cellBlockingStub = HoneycombCellServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(defaultHeaders));
    }

    /**
     * Create a new builder for configuring a {@link GrpcSharedwallClient}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Invoke a shared method and return aggregated results as a Map.
     *
     * @param methodName the {@code @Sharedwall} method name
     * @param body       the payload (will be converted to Protobuf Struct)
     * @return reactive Mono with cell→result map
     */
    public Mono<Map<String, Object>> invoke(String methodName, Object body) {
        return invoke(methodName, body, null);
    }

    /**
     * Invoke a shared method with a specific version.
     */
    public Mono<Map<String, Object>> invoke(String methodName, Object body, String version) {
        return Mono.fromCallable(() -> {
            SharedwallInvokeRequest.Builder req = SharedwallInvokeRequest.newBuilder()
                    .setMethodName(methodName)
                    .setRequestId(java.util.UUID.randomUUID().toString());

            if (version != null) req.setVersion(version);
            if (fromCell != null) req.setFromCell(fromCell);

            if (body != null) {
                if (body instanceof String s) {
                    req.setRawJsonPayload(s);
                } else if (body instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) body;
                    req.setPayload(ProtoJsonConverter.mapToStruct(map));
                } else {
                    String json = objectMapper.writeValueAsString(body);
                    req.setRawJsonPayload(json);
                }
            }

            SharedwallInvokeResponse response = blockingStub
                    .withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS)
                    .invoke(req.build());

            if (response.getStatusCode() != 0 && !response.getError().isEmpty()) {
                throw new RuntimeException("gRPC invoke error: " + response.getError());
            }

            return ProtoJsonConverter.structToMap(response.getResults());
        });
    }

    /**
     * Invoke a shared method with typed response extraction.
     */
    public <T> Mono<T> invokeTyped(String methodName, Object body, Class<T> responseType,
                                   String targetCell) {
        return invoke(methodName, body).map(envelope -> {
            Object cellResult;
            if (targetCell != null && envelope.containsKey(targetCell)) {
                cellResult = envelope.get(targetCell);
            } else {
                // Take first entry
                cellResult = envelope.values().stream().findFirst().orElse(null);
            }
            if (cellResult == null) return null;

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) cellResult;
            Object result = resultMap.get("result");

            if (responseType == Object.class) {
                @SuppressWarnings("unchecked")
                T cast = (T) result;
                return cast;
            }
            return objectMapper.convertValue(result, responseType);
        });
    }

    /**
     * Server-streaming invoke — returns a Flux of per-cell results.
     */
    public Flux<Map<String, Object>> invokeStream(String methodName, Object body) {
        return Flux.create(sink -> {
            try {
                SharedwallInvokeRequest.Builder req = SharedwallInvokeRequest.newBuilder()
                        .setMethodName(methodName)
                        .setRequestId(java.util.UUID.randomUUID().toString());

                if (fromCell != null) req.setFromCell(fromCell);

                if (body instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) body;
                    req.setPayload(ProtoJsonConverter.mapToStruct(map));
                } else if (body instanceof String s) {
                    req.setRawJsonPayload(s);
                } else if (body != null) {
                    req.setRawJsonPayload(objectMapper.writeValueAsString(body));
                }

                var iterator = blockingStub
                        .withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS)
                        .invokeStream(req.build());

                while (iterator.hasNext()) {
                    SharedwallInvokeResponse resp = iterator.next();
                    sink.next(ProtoJsonConverter.structToMap(resp.getResults()));
                }
                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    /**
     * List all invokable methods on the remote cell.
     */
    public Mono<List<SharedwallInvokeInfo>> listMethods() {
        return listMethods(null);
    }

    /**
     * List invokable methods, optionally filtered by version.
     */
    public Mono<List<SharedwallInvokeInfo>> listMethods(String version) {
        return Mono.fromCallable(() -> {
            ListMethodsRequest.Builder req = ListMethodsRequest.newBuilder();
            if (version != null) req.setVersion(version);

            ListMethodsResponse response = blockingStub
                    .withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS)
                    .listMethods(req.build());

            List<SharedwallInvokeInfo> result = new ArrayList<>();
            for (SharedMethodInfo m : response.getMethodsList()) {
                List<com.honeycomb.core.dto.SharedMethodInfo.ParameterInfo> params = new ArrayList<>();
                for (var p : m.getParametersList()) {
                    params.add(new com.honeycomb.core.dto.SharedMethodInfo.ParameterInfo(p.getName(), p.getType()));
                }
                result.add(new SharedwallInvokeInfo(
                        m.getCellName(),
                        m.getMethodName(),
                        m.getPath(),
                        m.getReturnType(),
                        params,
                        m.getAllowedFromList(),
                        m.getVersion(),
                        m.getDeprecated(),
                        m.getResiliencePolicy()
                ));
            }
            return result;
        });
    }

    /**
     * Gracefully shut down the underlying gRPC channel.
     */
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
            try {
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Builder for {@link GrpcSharedwallClient}.
     */
    public static final class Builder {
        private String target = "localhost:9090";
        private String fromCell;
        private Duration deadline = Duration.ofSeconds(10);
        private ObjectMapper objectMapper;
        private String negotiationType = "plaintext";
        private String loadBalancingPolicy = "round_robin";

        public Builder target(String target) {
            this.target = target;
            return this;
        }

        public Builder fromCell(String fromCell) {
            this.fromCell = fromCell;
            return this;
        }

        public Builder deadline(Duration deadline) {
            this.deadline = deadline;
            return this;
        }

        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        public Builder negotiationType(String negotiationType) {
            this.negotiationType = negotiationType;
            return this;
        }

        public Builder loadBalancingPolicy(String loadBalancingPolicy) {
            this.loadBalancingPolicy = loadBalancingPolicy;
            return this;
        }

        /**
         * Populate builder from {@link HoneycombGrpcProperties} for a given cell.
         */
        public Builder fromProperties(HoneycombGrpcProperties props, String cellName) {
            var client = props.getClient();
            this.deadline = client.getDeadline();
            this.negotiationType = client.getNegotiationType();
            this.loadBalancingPolicy = client.getLoadBalancingPolicy();

            String cellTarget = client.getPerCellTargets().get(cellName);
            this.target = cellTarget != null ? cellTarget : client.getDefaultTarget();

            return this;
        }

        public GrpcSharedwallClient build() {
            return new GrpcSharedwallClient(this);
        }
    }
}
