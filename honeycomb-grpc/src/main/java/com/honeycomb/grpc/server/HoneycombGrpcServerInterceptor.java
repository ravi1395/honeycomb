package com.honeycomb.grpc.server;

import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Server-side gRPC interceptor that extracts Honeycomb metadata from gRPC
 * request headers and populates SLF4J MDC for structured logging.
 *
 * <p>Extracts:</p>
 * <ul>
 *   <li>{@code x-request-id} → MDC {@code requestId} (auto-generated if absent)</li>
 *   <li>{@code x-from-cell}  → MDC {@code fromCell}</li>
 *   <li>{@code x-api-key}    → MDC {@code apiKey}</li>
 * </ul>
 *
 * @since 1.4.0
 */
public class HoneycombGrpcServerInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(HoneycombGrpcServerInterceptor.class);

    public static final Metadata.Key<String> REQUEST_ID_KEY =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> FROM_CELL_KEY =
            Metadata.Key.of("x-from-cell", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> API_KEY_KEY =
            Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> SHARED_VERSION_KEY =
            Metadata.Key.of("x-shared-version", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> IDEMPOTENCY_KEY =
            Metadata.Key.of("idempotency-key", Metadata.ASCII_STRING_MARSHALLER);

    /** gRPC Context key for Request ID propagation. */
    public static final Context.Key<String> CTX_REQUEST_ID = Context.key("honeycomb-request-id");
    public static final Context.Key<String> CTX_FROM_CELL = Context.key("honeycomb-from-cell");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String requestId = headers.get(REQUEST_ID_KEY);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        String fromCell = headers.get(FROM_CELL_KEY);

        // Populate gRPC Context for downstream services
        Context ctx = Context.current()
                .withValue(CTX_REQUEST_ID, requestId)
                .withValue(CTX_FROM_CELL, fromCell);

        // Populate MDC for logging
        MDC.put("requestId", requestId);
        if (fromCell != null) MDC.put("fromCell", fromCell);

        log.debug("gRPC request: method={}, requestId={}, fromCell={}",
                call.getMethodDescriptor().getFullMethodName(), requestId, fromCell);

        return Contexts.interceptCall(ctx, call, headers, next);
    }
}
