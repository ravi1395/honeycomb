package com.example.honeycomb.dto;

import org.springframework.lang.Nullable;

/**
 * Runtime status for a cell server.
 */
public record CellRuntimeStatus(
        String name,
        @Nullable Integer configuredPort,
        @Nullable Integer runningPort,
        @Nullable Integer managementPort,
        boolean running,
        boolean managementRunning,
        @Nullable Integer runningManagementPort
) {
}
