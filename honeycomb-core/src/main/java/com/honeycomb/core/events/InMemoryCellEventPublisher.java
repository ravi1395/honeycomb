package com.honeycomb.core.events;

import com.honeycomb.core.dto.CellEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory event bus for development and single-instance deployments.
 * Events are broadcast via Reactor Sinks; no external broker is required.
 *
 * <p><b>Added in v1.3</b> — default transport for the cell event bus.</p>
 *
 * <p>Architecture: uses a dual-sink approach — a single global sink receives
 * all events, while per-topic sinks allow consumers to filter by topic.
 * Topics are lazily created on first subscription via {@link #subscribe(String)}.</p>
 *
 * <p>Back-pressure strategy: {@code onBackpressureBuffer(256)} — if the consumer
 * falls behind, up to 256 events are buffered before overflow signalling.</p>
 *
 * <p>Not suitable for multi-instance deployments; use {@code RedisCellEventPublisher}
 * with {@code honeycomb.events.transport=redis} instead.</p>
 */
public class InMemoryCellEventPublisher implements CellEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(InMemoryCellEventPublisher.class);

    private static final String DEFAULT_TOPIC = "honeycomb.events";

    // Per-topic sinks: lazily created when a consumer subscribes to a specific topic
    private final Map<String, Sinks.Many<CellEvent>> topicSinks = new ConcurrentHashMap<>();

    // Global sink: receives every published event regardless of topic
    private final Sinks.Many<CellEvent> globalSink;

    // Micrometer counters for observability
    private final Counter publishCounter;
    private final Counter publishErrorCounter;

    public InMemoryCellEventPublisher(MeterRegistry meterRegistry) {
        // multicast() allows multiple subscribers; onBackpressureBuffer prevents data loss
        this.globalSink = Sinks.many().multicast().onBackpressureBuffer(256);
        this.publishCounter = meterRegistry.counter("honeycomb.events.published", "transport", "memory");
        this.publishErrorCounter = meterRegistry.counter("honeycomb.events.publish.errors", "transport", "memory");
    }

    @Override
    public Mono<Void> publish(CellEvent event) {
        return publish(DEFAULT_TOPIC, event);
    }

    @Override
    public Mono<Void> publish(String topic, CellEvent event) {
        return Mono.fromRunnable(() -> {
            try {
                // Emit to global sink
                Sinks.EmitResult globalResult = globalSink.tryEmitNext(event);
                if (globalResult.isFailure()) {
                    log.warn("Failed to emit event to global sink: {} (event={})", globalResult, event.type());
                }

                // Emit to topic-specific sink
                Sinks.Many<CellEvent> topicSink = topicSinks.get(topic);
                if (topicSink != null) {
                    Sinks.EmitResult topicResult = topicSink.tryEmitNext(event);
                    if (topicResult.isFailure()) {
                        log.warn("Failed to emit event to topic '{}': {}", topic, topicResult);
                    }
                }

                publishCounter.increment();
                log.debug("Published event: type={}, source={}, topic={}, id={}",
                        event.type(), event.sourceCell(), topic, event.id());
            } catch (Exception ex) {
                publishErrorCounter.increment();
                log.error("Error publishing event: {}", ex.getMessage(), ex);
            }
        });
    }

    @Override
    public Flux<CellEvent> subscribe() {
        return globalSink.asFlux();
    }

    @Override
    public Flux<CellEvent> subscribe(String topic) {
        Sinks.Many<CellEvent> sink = topicSinks.computeIfAbsent(topic,
                k -> Sinks.many().multicast().onBackpressureBuffer(256));
        return sink.asFlux();
    }
}
