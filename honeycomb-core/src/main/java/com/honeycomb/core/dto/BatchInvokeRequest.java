package com.honeycomb.core.dto;

import java.util.Map;

/**
 * Request DTO for a single shared method invocation in a batch.
 *
 * @param methodName the shared method alias to invoke
 * @param version    optional version (defaults to "v1")
 * @param body       optional request payload
 */
public record BatchInvokeRequest(
        String methodName,
        String version,
        Map<String, Object> body
) {
    public BatchInvokeRequest {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName is required");
        }
        version = (version == null || version.isBlank()) ? "v1" : version;
    }
}
