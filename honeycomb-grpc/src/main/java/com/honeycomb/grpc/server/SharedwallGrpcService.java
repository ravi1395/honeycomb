package com.honeycomb.grpc.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Struct;
import com.honeycomb.core.annotations.Cell;
import com.honeycomb.core.annotations.Sharedwall;
import com.honeycomb.core.service.SharedwallMethodCache;
import com.honeycomb.grpc.proto.*;
import com.honeycomb.grpc.util.ProtoJsonConverter;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * gRPC service implementation for {@code @Sharedwall} method invocation.
 *
 * <p>Mirrors the functionality of {@code SharedwallDispatcherController} but over
 * the gRPC transport. Reuses the same {@link SharedwallMethodCache} to discover
 * and invoke shared methods, maintaining full feature parity with the HTTP transport.</p>
 *
 * <h3>Endpoint Mapping</h3>
 * <table>
 *   <tr><th>HTTP</th><th>gRPC</th></tr>
 *   <tr><td>POST /honeycomb/shared/{method}</td><td>Invoke RPC</td></tr>
 *   <tr><td>GET /honeycomb/shared/methods</td><td>ListMethods RPC</td></tr>
 *   <tr><td>POST (streaming)</td><td>InvokeStream RPC</td></tr>
 * </table>
 *
 * @since 1.4.0
 * @see SharedwallMethodCache
 */
@GrpcService
public class SharedwallGrpcService extends HoneycombSharedwallServiceGrpc.HoneycombSharedwallServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(SharedwallGrpcService.class);

    private final SharedwallMethodCache methodCache;
    private final ObjectMapper objectMapper;

    public SharedwallGrpcService(SharedwallMethodCache methodCache, ObjectMapper objectMapper) {
        this.methodCache = methodCache;
        this.objectMapper = objectMapper;
    }

    /**
     * Invoke a shared method by name. Resolves candidates from the method cache,
     * deserialises the Protobuf Struct payload to JSON, invokes reflectively,
     * and returns the aggregated result as a Struct.
     */
    @Override
    public void invoke(SharedwallInvokeRequest request, StreamObserver<SharedwallInvokeResponse> responseObserver) {
        String methodName = request.getMethodName();
        String version = request.getVersion().isEmpty() ? "v1" : request.getVersion();
        String fromCell = request.getFromCell();
        String requestId = request.getRequestId();

        log.debug("gRPC Invoke: method={}, version={}, from={}, requestId={}", methodName, version, fromCell, requestId);

        try {
            // Resolve candidates from method cache
            List<SharedwallMethodCache.MethodCandidate> candidates = methodCache.getCandidates(methodName, version);
            if (candidates == null || candidates.isEmpty()) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("No shared method found: " + methodName + " version=" + version)
                        .asRuntimeException());
                return;
            }

            // Parse payload
            String jsonPayload = resolvePayload(request);
            JsonNode payloadNode = null;
            if (jsonPayload != null && !jsonPayload.isBlank()) {
                payloadNode = objectMapper.readTree(jsonPayload);
            }

            // Invoke all candidates and aggregate results
            Map<String, Object> aggregated = new LinkedHashMap<>();
            for (SharedwallMethodCache.MethodCandidate candidate : candidates) {
                String cellName = deriveCellName(candidate.getBean());
                // Check ACL
                if (!isCallerAllowed(candidate, fromCell)) {
                    log.debug("Caller {} not allowed for {}.{}", fromCell, cellName, methodName);
                    continue;
                }

                try {
                    Object result = invokeCandidate(candidate, payloadNode);
                    aggregated.put(cellName, Map.of("result", result != null ? result : Map.of()));
                } catch (Exception e) {
                    log.warn("gRPC invocation error on {}.{}: {}", cellName, methodName, e.getMessage());
                    aggregated.put(cellName, Map.of("error", e.getMessage()));
                }
            }

            // Build response
            Struct resultStruct = ProtoJsonConverter.mapToStruct(aggregated);
            SharedwallInvokeResponse response = SharedwallInvokeResponse.newBuilder()
                    .setResults(resultStruct)
                    .setRequestId(requestId)
                    .setStatusCode(0)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC Invoke failed for method={}: {}", methodName, e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Invocation failed: " + e.getMessage())
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    /**
     * List all registered shared methods, optionally filtered by version.
     */
    @Override
    public void listMethods(ListMethodsRequest request, StreamObserver<ListMethodsResponse> responseObserver) {
        try {
            String versionFilter = request.getVersion().isEmpty() ? null : request.getVersion();
            Map<String, List<SharedwallMethodCache.MethodCandidate>> allCandidates =
                    versionFilter != null ? methodCache.getAllCandidates(versionFilter)
                                          : methodCache.getAllCandidates();

            ListMethodsResponse.Builder responseBuilder = ListMethodsResponse.newBuilder();

            for (Map.Entry<String, List<SharedwallMethodCache.MethodCandidate>> entry : allCandidates.entrySet()) {
                String alias = entry.getKey();
                for (SharedwallMethodCache.MethodCandidate candidate : entry.getValue()) {
                    String cellName = deriveCellName(candidate.getBean());
                    Sharedwall sw = candidate.getSharedwall();
                    Method method = candidate.getMethod();

                    SharedMethodInfo.Builder methodInfo = SharedMethodInfo.newBuilder()
                            .setCellName(cellName)
                            .setMethodName(alias)
                            .setPath("/honeycomb/shared/" + alias)
                            .setReturnType(method.getReturnType().getName())
                            .setVersion(sw.version() != null ? sw.version() : "v1")
                            .setDeprecated(method.isAnnotationPresent(Deprecated.class))
                            .setResiliencePolicy("default")
                            .addAllAllowedFrom(Arrays.asList(sw.allowedFrom()));

                    Parameter[] params = method.getParameters();
                    for (int i = 0; i < params.length; i++) {
                        methodInfo.addParameters(ParameterInfo.newBuilder()
                                .setName(params[i].getName())
                                .setType(params[i].getType().getName())
                                .setIndex(i)
                                .build());
                    }

                    responseBuilder.addMethods(methodInfo.build());
                }
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC ListMethods failed: {}", e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to list methods: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * Server-streaming invoke — sends one response per candidate cell.
     */
    @Override
    public void invokeStream(SharedwallInvokeRequest request, StreamObserver<SharedwallInvokeResponse> responseObserver) {
        String methodName = request.getMethodName();
        String version = request.getVersion().isEmpty() ? "v1" : request.getVersion();
        String fromCell = request.getFromCell();
        String requestId = request.getRequestId();

        try {
            List<SharedwallMethodCache.MethodCandidate> candidates = methodCache.getCandidates(methodName, version);
            if (candidates == null || candidates.isEmpty()) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("No shared method found: " + methodName)
                        .asRuntimeException());
                return;
            }

            String jsonPayload = resolvePayload(request);
            JsonNode payloadNode = (jsonPayload != null && !jsonPayload.isBlank())
                    ? objectMapper.readTree(jsonPayload) : null;

            for (SharedwallMethodCache.MethodCandidate candidate : candidates) {
                String cellName = deriveCellName(candidate.getBean());
                if (!isCallerAllowed(candidate, fromCell)) continue;

                try {
                    Object result = invokeCandidate(candidate, payloadNode);
                    Map<String, Object> cellResult = Map.of(cellName,
                            Map.of("result", result != null ? result : Map.of()));

                    responseObserver.onNext(SharedwallInvokeResponse.newBuilder()
                            .setResults(ProtoJsonConverter.mapToStruct(cellResult))
                            .setRequestId(requestId)
                            .setStatusCode(0)
                            .build());
                } catch (Exception e) {
                    responseObserver.onNext(SharedwallInvokeResponse.newBuilder()
                            .setResults(ProtoJsonConverter.mapToStruct(
                                    Map.of(cellName, Map.of("error", e.getMessage()))))
                            .setRequestId(requestId)
                            .setStatusCode(Status.INTERNAL.getCode().value())
                            .setError(e.getMessage())
                            .build());
                }
            }
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC InvokeStream failed: {}", e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    // ───────── Internal helpers ─────────

    /**
     * Derive the cell name from the bean's {@code @Cell} annotation, falling
     * back to the simple class name.
     */
    private String deriveCellName(Object bean) {
        Class<?> clazz = AopUtils.getTargetClass(bean);
        Cell cellAnno = clazz.getAnnotation(Cell.class);
        if (cellAnno != null && cellAnno.value() != null && !cellAnno.value().isBlank()) {
            return cellAnno.value();
        }
        return clazz.getSimpleName();
    }

    private String resolvePayload(SharedwallInvokeRequest request) {
        // Prefer raw JSON string if provided
        if (!request.getRawJsonPayload().isEmpty()) {
            return request.getRawJsonPayload();
        }
        // Otherwise convert Struct to JSON
        if (request.hasPayload() && request.getPayload().getFieldsCount() > 0) {
            return ProtoJsonConverter.structToJson(request.getPayload(), objectMapper);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object invokeCandidate(SharedwallMethodCache.MethodCandidate candidate, JsonNode payload)
            throws Exception {
        Object bean = candidate.getBean();
        Method method = candidate.getMethod();
        Parameter[] params = method.getParameters();

        Object[] args;
        if (params.length == 0) {
            args = new Object[0];
        } else if (params.length == 1) {
            args = new Object[]{deserializeArg(payload, params[0].getType())};
        } else {
            args = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                if (payload != null && payload.isArray() && i < payload.size()) {
                    args[i] = objectMapper.treeToValue(payload.get(i), params[i].getType());
                } else if (payload != null && payload.isObject() && payload.has(params[i].getName())) {
                    args[i] = objectMapper.treeToValue(payload.get(params[i].getName()), params[i].getType());
                } else {
                    args[i] = null;
                }
            }
        }

        // Use the fast invoker if available, otherwise fall back to reflection
        Object raw;
        SharedwallMethodCache.MethodCandidate.Invoker invoker = candidate.getInvoker();
        if (invoker != null) {
            try {
                raw = invoker.invoke(bean, args);
            } catch (Throwable t) {
                if (t instanceof Exception ex) throw ex;
                throw new RuntimeException(t);
            }
        } else {
            raw = method.invoke(bean, args);
        }

        // Unwrap reactive types
        if (raw instanceof reactor.core.publisher.Mono<?> mono) {
            return mono.block();
        }
        if (raw instanceof reactor.core.publisher.Flux<?> flux) {
            return flux.collectList().block();
        }
        return raw;
    }

    private Object deserializeArg(JsonNode payload, Class<?> targetType) throws JsonProcessingException {
        if (payload == null) return null;
        if (targetType == String.class) return objectMapper.writeValueAsString(payload);
        if (targetType == JsonNode.class) return payload;
        return objectMapper.treeToValue(payload, targetType);
    }

    private boolean isCallerAllowed(SharedwallMethodCache.MethodCandidate candidate, String fromCell) {
        String[] allowedFrom = candidate.getSharedwall().allowedFrom();
        if (allowedFrom == null || allowedFrom.length == 0) return true;
        if (fromCell == null || fromCell.isBlank()) return false;
        for (String allowed : allowedFrom) {
            if ("*".equals(allowed) || fromCell.equals(allowed)) return true;
        }
        return false;
    }
}
