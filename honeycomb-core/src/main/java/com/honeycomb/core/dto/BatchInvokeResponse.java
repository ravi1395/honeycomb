package com.honeycomb.core.dto;

import java.util.Map;

/**
 * Response DTO for a single invocation within a batch.
 *
 * @param methodName the shared method that was invoked
 * @param version    the version used
 * @param status     "ok" or "error"
 * @param result     the invocation result (per-cell map)
 * @param error      error message if status is "error", null otherwise
 * @param durationMs wall-clock duration of the invocation in milliseconds
 */
public record BatchInvokeResponse(
        String methodName,
        String version,
        String status,
        Map<String, Object> result,
        String error,
        long durationMs
) {}
