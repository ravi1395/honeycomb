package com.honeycomb.core.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable DTO representing an audit trail entry for Honeycomb operations.
 * Streamed via WebSocket at {@code ws://host/honeycomb/ws/events} and
 * retrievable via {@code GET /honeycomb/audit}.
 *
 * @param timestamp when the event occurred
 * @param actor     the principal or API key identity that triggered the action
 * @param action    the operation performed (e.g. "create", "invoke", "delete")
 * @param cell      the cell name involved (may be {@code null} for system events)
 * @param status    outcome of the operation ("success" or "error")
 * @param details   additional context (payload, error message, etc.)
 */
public record AuditEvent(
        Instant timestamp,
        String actor,
        String action,
        String cell,
        String status,
        Map<String, Object> details
) {
}
