package com.honeycomb.core.web;

import com.honeycomb.core.dto.CellEvent;
import com.honeycomb.core.events.CellEventPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * REST endpoints for the cell event bus.
 * Provides SSE streaming and manual event publishing.
 *
 * <p><b>Added in v1.3</b> — exposes the event bus over HTTP.</p>
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /honeycomb/events/stream} — SSE stream of all events</li>
 *   <li>{@code GET /honeycomb/events/stream/{topic}} — SSE stream for a specific topic</li>
 *   <li>{@code POST /honeycomb/events/publish} — publish a custom event via HTTP</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/honeycomb/events")
@Tag(name = "Cell Events", description = "Event bus for inter-cell async communication")
public class CellEventController {

    private final CellEventPublisher eventPublisher;

    public CellEventController(CellEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * SSE stream of all cell events in real time.
     */
    @Operation(summary = "Stream cell events (SSE)")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<CellEvent> streamEvents() {
        return eventPublisher.subscribe();
    }

    /**
     * SSE stream for a specific event topic.
     */
    @Operation(summary = "Stream cell events for a specific topic (SSE)")
    @GetMapping(value = "/stream/{topic}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<CellEvent> streamTopicEvents(@PathVariable String topic) {
        return eventPublisher.subscribe(topic);
    }

    /**
     * Publish a custom event manually via HTTP.
     * Accepts a JSON body with: type, sourceCell, payload, correlationId, topic.
     * All fields are optional with sensible defaults.
     */
    @Operation(summary = "Publish a custom cell event")
    @PostMapping("/publish")
    public Mono<Map<String, Object>> publishEvent(@RequestBody Map<String, Object> body) {
        // Extract event fields with defaults; allows partial payloads
        String type = (String) body.getOrDefault("type", CellEvent.TYPE_CUSTOM);
        String sourceCell = (String) body.getOrDefault("sourceCell", "api");
        String correlationId = (String) body.get("correlationId");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) body.getOrDefault("payload", Map.of());

        // Use explicit correlation ID if provided (for tracing), otherwise auto-generate
        CellEvent event = correlationId != null
                ? CellEvent.of(type, sourceCell, payload, correlationId)
                : CellEvent.of(type, sourceCell, payload);

        // Route to the specified topic (defaults to the global topic)
        String topic = (String) body.getOrDefault("topic", "honeycomb.events");

        return eventPublisher.publish(topic, event)
                .thenReturn(Map.of(
                        "status", "published",
                        "eventId", event.id(),
                        "type", event.type()
                ));
    }
}
