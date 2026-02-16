package com.honeycomb.core.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Standardized event envelope for inter-cell async communication.
 * Every cell event carries a type, source cell, payload, timestamp, and correlation ID.
 *
 * <p><b>Added in v1.3</b> — part of event-driven cell communication feature.</p>
 *
 * <p>Well-known types follow a domain-dot-action convention (e.g. {@code item.created}).
 * Custom events should use the {@link #TYPE_CUSTOM} type or define their own constants.</p>
 */
public record CellEvent(
        String id,
        String type,
        String sourceCell,
        Map<String, Object> payload,
        Instant timestamp,
        String correlationId
) {
    /**
     * Create a new event with auto-generated ID and current timestamp.
     * A random correlation ID is generated for distributed tracing.
     */
    public static CellEvent of(String type, String sourceCell, Map<String, Object> payload) {
        return new CellEvent(
                UUID.randomUUID().toString(),
                type,
                sourceCell,
                payload == null ? Map.of() : payload,
                Instant.now(),
                UUID.randomUUID().toString()  // auto-generated correlation ID for tracing
        );
    }

    /**
     * Create a new event with an explicit correlation ID (for tracing chains).
     * Use this variant when propagating an existing correlation through a chain of events.
     */
    public static CellEvent of(String type, String sourceCell, Map<String, Object> payload, String correlationId) {
        return new CellEvent(
                UUID.randomUUID().toString(),
                type,
                sourceCell,
                payload == null ? Map.of() : payload,
                Instant.now(),
                correlationId
        );
    }

    // ─── Well-known event types ─────────────────────────────────
    // These constants follow the convention: domain.action
    // Framework publishes these automatically; listeners can filter by type.

    // Cell lifecycle events
    public static final String TYPE_CELL_REGISTERED = "cell.registered";
    public static final String TYPE_CELL_DEREGISTERED = "cell.deregistered";
    public static final String TYPE_CELL_STARTED = "cell.server.started";
    public static final String TYPE_CELL_STOPPED = "cell.server.stopped";

    // CRUD item events
    public static final String TYPE_ITEM_CREATED = "item.created";
    public static final String TYPE_ITEM_UPDATED = "item.updated";
    public static final String TYPE_ITEM_DELETED = "item.deleted";

    // Shared method events
    public static final String TYPE_SHARED_INVOKED = "shared.invoked";
    public static final String TYPE_SHARED_FAILED = "shared.failed";

    // Cache events
    public static final String TYPE_CACHE_REFRESHED = "cache.refreshed";
    public static final String TYPE_CACHE_INVALIDATED = "cache.invalidated";

    // Custom user-defined events
    public static final String TYPE_CUSTOM = "custom";
}
