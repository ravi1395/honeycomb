package com.honeycomb.core.events;

import com.honeycomb.core.dto.CellEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Abstraction for publishing cell events.
 * Implementations may use Redis pub/sub, in-memory Reactor Sinks, or other brokers.
 *
 * <p><b>Added in v1.3</b> — core interface for the event-driven communication feature.</p>
 *
 * <p>Two implementations are provided out of the box:
 * <ul>
 *   <li>{@code InMemoryCellEventPublisher} — default, no external dependencies</li>
 *   <li>{@code RedisCellEventPublisher} — for multi-instance deployments via Redis pub/sub</li>
 * </ul>
 * Selection is controlled by {@code honeycomb.events.transport} property.</p>
 */
public interface CellEventPublisher {

    /**
     * Publish an event to the event bus.
     *
     * @param event the cell event to publish
     * @return Mono completing when the event has been accepted by the transport
     */
    Mono<Void> publish(CellEvent event);

    /**
     * Publish an event to a specific topic/channel.
     *
     * @param topic the target topic
     * @param event the cell event
     * @return Mono completing when accepted
     */
    Mono<Void> publish(String topic, CellEvent event);

    /**
     * Subscribe to events from the bus (all topics).
     *
     * @return reactive stream of cell events
     */
    Flux<CellEvent> subscribe();

    /**
     * Subscribe to events for a specific topic/channel.
     *
     * @param topic the topic to subscribe to
     * @return reactive stream of cell events for that topic
     */
    Flux<CellEvent> subscribe(String topic);
}
