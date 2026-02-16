package com.honeycomb.core.events;

import com.honeycomb.core.annotations.Cell;
import com.honeycomb.core.dto.CellEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for {@link CellEventListenerRegistry} discovering @CellEventListener methods.
 */
@SpringBootTest(classes = { com.honeycomb.core.HoneycombApplication.class,
        CellEventListenerRegistryTest.TestConfig.class })
class CellEventListenerRegistryTest {

    @Autowired
    private CellEventPublisher eventPublisher;

    @SuppressWarnings("unused")
    @Autowired
    private CellEventListenerRegistry registry;

    @Autowired
    private TestEventCollector collector;

    @TestConfiguration
    static class TestConfig {

        @Bean
        TestEventCollector testEventCollector() {
            return new TestEventCollector();
        }

        @Bean
        TestListenerBean testListenerBean(TestEventCollector collector) {
            return new TestListenerBean(collector);
        }
    }

    static class TestEventCollector {
        final CopyOnWriteArrayList<CellEvent> received = new CopyOnWriteArrayList<>();
    }

    @Cell("TestListenerCell")
    static class TestListenerBean {
        private final TestEventCollector collector;

        TestListenerBean(TestEventCollector col) {
            this.collector = col;
        }

        @CellEventListener(CellEvent.TYPE_CUSTOM)
        public Mono<Void> onCustomEvent(CellEvent event) {
            collector.received.add(event);
            return Mono.empty();
        }
    }

    @Test
    void discoversAndRoutesEvent() throws InterruptedException {
        CellEvent event = CellEvent.of(CellEvent.TYPE_CUSTOM, "sender", Map.of("msg", "hello"));
        // Publish through the event bus — the registry subscribes and routes automatically
        eventPublisher.publish(event).block();

        // Allow time for async routing
        for (int i = 0; i < 20; i++) {
            if (!collector.received.isEmpty()) break;
            Thread.sleep(100);
        }

        assertFalse(collector.received.isEmpty(), "Expected at least one routed event");
        assertEquals(event.id(), collector.received.get(0).id());
    }

    @Test
    void ignoresUnmatchedEventType() throws InterruptedException {
        int before = collector.received.size();
        CellEvent event = CellEvent.of("unknown.type", "sender", Map.of());
        eventPublisher.publish(event).block();

        Thread.sleep(300);
        long count = collector.received.stream()
                .skip(before)
                .filter(e -> "unknown.type".equals(e.type()))
                .count();
        assertEquals(0, count);
    }
}
