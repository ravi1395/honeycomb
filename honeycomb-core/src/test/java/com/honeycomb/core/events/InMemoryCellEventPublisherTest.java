package com.honeycomb.core.events;

import com.honeycomb.core.dto.CellEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link InMemoryCellEventPublisher}.
 */
class InMemoryCellEventPublisherTest {

    private InMemoryCellEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new InMemoryCellEventPublisher(new SimpleMeterRegistry());
    }

    @Test
    void publishAndSubscribeGlobal() {
        CellEvent event = CellEvent.of(CellEvent.TYPE_CUSTOM, "testCell", Map.of("key", "value"));

        // Subscribe first, then publish
        StepVerifier.create(publisher.subscribe().take(1))
                .then(() -> publisher.publish(event).subscribe())
                .assertNext(received -> {
                    assertEquals(event.id(), received.id());
                    assertEquals(CellEvent.TYPE_CUSTOM, received.type());
                    assertEquals("testCell", received.sourceCell());
                    assertEquals("value", received.payload().get("key"));
                })
                .verifyComplete();
    }

    @Test
    void publishAndSubscribeByTopic() {
        CellEvent event = CellEvent.of("my.event", "cellA", Map.of("data", "hello"));

        StepVerifier.create(publisher.subscribe("my-topic").take(1))
                .then(() -> publisher.publish("my-topic", event).subscribe())
                .assertNext(received -> {
                    assertEquals(event.id(), received.id());
                    assertEquals("my.event", received.type());
                })
                .verifyComplete();
    }

    @Test
    void publishToTopicDoesNotLeakToOtherTopics() {
        CellEvent event = CellEvent.of("test", "cell", Map.of());

        StepVerifier.create(publisher.subscribe("other-topic").take(Duration.ofMillis(200)))
                .then(() -> publisher.publish("my-topic", event).subscribe())
                .verifyComplete();
    }

    @Test
    void eventFieldsAreAutoPopulated() {
        CellEvent event = CellEvent.of("test.type", "myCell", Map.of("x", 1));
        assertNotNull(event.id());
        assertFalse(event.id().isEmpty());
        assertNotNull(event.timestamp());
        assertEquals("test.type", event.type());
        assertEquals("myCell", event.sourceCell());
    }

    @Test
    void eventWithCorrelationId() {
        CellEvent event = CellEvent.of("test", "cell", Map.of(), "corr-123");
        assertEquals("corr-123", event.correlationId());
    }
}
