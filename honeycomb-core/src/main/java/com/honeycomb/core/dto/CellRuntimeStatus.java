package com.honeycomb.core.dto;

import org.springframework.lang.Nullable;

/**
 * Immutable DTO representing the runtime status of a per-cell HTTP server.
 * Returned by {@code GET /honeycomb/cells} and used by the admin UI.
 *
 * @param name              the cell's exposed name
 * @param configuredPort    the port configured in YAML / annotation ({@code null} if none)
 * @param runningPort       the actual port the server is bound to ({@code null} if stopped)
 * @param managementPort    the configured actuator management port ({@code null} if none)
 * @param running           whether the cell server is currently accepting connections
 * @param managementRunning whether the management server is currently accepting connections
 * @param runningManagementPort the actual management port ({@code null} if stopped)
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
