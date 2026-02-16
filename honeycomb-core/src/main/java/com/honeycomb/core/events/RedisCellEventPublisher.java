package com.honeycomb.core.events;

import com.honeycomb.core.dto.CellEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Redis pub/sub based event publisher for multi-instance deployments.
 * Events are serialized to JSON and published to Redis channels.
 * Subscribers receive events from all instances.
 *
 * <p><b>Added in v1.3</b> — activated via {@code honeycomb.events.transport=redis}.</p>
 *
 * <p>Architecture: uses a dual-publish pattern — every event is sent to both
 * a topic-specific channel ({@code honeycomb:events:<topic>}) and the global
 * channel ({@code honeycomb:events}). This allows consumers to subscribe to
 * all events or filter by topic.</p>
 *
 * <p>Serialization: events are serialized/deserialized as JSON using Jackson
 * {@link ObjectMapper}, making the wire format language-agnostic.</p>
 */
@SuppressWarnings("null")
public class RedisCellEventPublisher implements CellEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(RedisCellEventPublisher.class);

    // Default Redis channel for all events (global stream)
    private static final String DEFAULT_CHANNEL = "honeycomb:events";
    // Prefix for topic-specific channels
    private static final String CHANNEL_PREFIX = "honeycomb:events:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ReactiveRedisConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;
    private final Counter publishCounter;
    private final Counter publishErrorCounter;

    public RedisCellEventPublisher(ReactiveStringRedisTemplate redisTemplate,
                                   ReactiveRedisConnectionFactory connectionFactory,
                                   ObjectMapper objectMapper,
                                   MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.connectionFactory = connectionFactory;
        this.objectMapper = objectMapper;
        this.publishCounter = meterRegistry.counter("honeycomb.events.published", "transport", "redis");
        this.publishErrorCounter = meterRegistry.counter("honeycomb.events.publish.errors", "transport", "redis");
    }

    @Override
    public Mono<Void> publish(CellEvent event) {
        return publish(DEFAULT_CHANNEL, event);
    }

    @Override
    public Mono<Void> publish(String topic, CellEvent event) {
        // Normalize the channel name: prepend prefix if not already present
        String channel = topic.startsWith(CHANNEL_PREFIX) ? topic : CHANNEL_PREFIX + topic;
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                // Dual-publish: send to topic-specific channel first, then to the global channel.
                // This ensures topic subscribers AND global subscribers both receive the event.
                .flatMap(json -> redisTemplate.convertAndSend(channel, json)
                        .then(redisTemplate.convertAndSend(DEFAULT_CHANNEL, json)))
                .doOnSuccess(v -> {
                    publishCounter.increment();
                    log.debug("Published event to Redis: type={}, source={}, channel={}", event.type(), event.sourceCell(), channel);
                })
                .doOnError(ex -> {
                    publishErrorCounter.increment();
                    log.error("Failed to publish event to Redis: {}", ex.getMessage(), ex);
                })
                .then();
    }

    @Override
    public Flux<CellEvent> subscribe() {
        return subscribeToChannel(DEFAULT_CHANNEL);
    }

    @Override
    public Flux<CellEvent> subscribe(String topic) {
        String channel = topic.startsWith(CHANNEL_PREFIX) ? topic : CHANNEL_PREFIX + topic;
        return subscribeToChannel(channel);
    }

    private Flux<CellEvent> subscribeToChannel(String channel) {
        ReactiveRedisMessageListenerContainer container =
                new ReactiveRedisMessageListenerContainer(connectionFactory);
        return container.receive(ChannelTopic.of(channel))
                .map(message -> {
                    try {
                        return objectMapper.readValue(message.getMessage(), CellEvent.class);
                    } catch (Exception ex) {
                        log.error("Failed to deserialize event from Redis: {}", ex.getMessage());
                        return null;
                    }
                })
                .filter(e -> e != null);
    }
}
