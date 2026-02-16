package com.honeycomb.grpc.client;

import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Client-side gRPC interceptor that attaches Honeycomb metadata headers
 * to every outgoing RPC call.
 *
 * <p>Automatically propagates:</p>
 * <ul>
 *   <li>{@code x-request-id} — generated UUID (or from MDC if available)</li>
 *   <li>{@code x-from-cell}  — calling cell identity</li>
 * </ul>
 *
 * @since 1.4.0
 */
public class HoneycombGrpcClientInterceptor implements ClientInterceptor {

    private static final Logger log = LoggerFactory.getLogger(HoneycombGrpcClientInterceptor.class);

    private static final Metadata.Key<String> REQUEST_ID_KEY =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> FROM_CELL_KEY =
            Metadata.Key.of("x-from-cell", Metadata.ASCII_STRING_MARSHALLER);

    private final String fromCell;

    public HoneycombGrpcClientInterceptor(String fromCell) {
        this.fromCell = fromCell;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                // Request ID: prefer MDC, fallback to new UUID
                String requestId = MDC.get("requestId");
                if (requestId == null || requestId.isBlank()) {
                    requestId = UUID.randomUUID().toString();
                }
                headers.put(REQUEST_ID_KEY, requestId);

                // From Cell
                if (fromCell != null && !fromCell.isBlank()) {
                    headers.put(FROM_CELL_KEY, fromCell);
                }

                log.debug("gRPC client call: method={}, requestId={}, fromCell={}",
                        method.getFullMethodName(), requestId, fromCell);

                super.start(responseListener, headers);
            }
        };
    }
}
