package com.honeycomb.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for {@link RedisCellDataStore} using Testcontainers.
 *
 * <p>Starts a real Redis 7 container and exercises all CRUD operations
 * against the reactive store to verify serialisation, key namespacing,
 * and delete behaviour.</p>
 *
 * @since 1.4.3
 */
@Testcontainers
class RedisCellDataStoreIT {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private RedisCellDataStore store;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        ReactiveStringRedisTemplate template = new ReactiveStringRedisTemplate(factory);
        store = new RedisCellDataStore(template, new ObjectMapper());
    }

    @Test
    @DisplayName("create and get round-trips a cell item")
    void createAndGet() {
        Map<String, Object> payload = Map.of("id", "item-1", "name", "test");

        StepVerifier.create(store.create("testCell", payload))
                .assertNext(created -> {
                    assertEquals("item-1", created.get("id"));
                    assertEquals("test", created.get("name"));
                })
                .verifyComplete();

        StepVerifier.create(store.get("testCell", "item-1"))
                .assertNext(item -> assertEquals("test", item.get("name")))
                .verifyComplete();
    }

    @Test
    @DisplayName("create auto-generates ID when not provided")
    void createAutoId() {
        Map<String, Object> payload = Map.of("name", "auto");

        StepVerifier.create(store.create("testCell", payload))
                .assertNext(created -> assertNotNull(created.get("id")))
                .verifyComplete();
    }

    @Test
    @DisplayName("list returns all items for a cell")
    void list() {
        store.create("listCell", Map.of("id", "a", "v", 1)).block();
        store.create("listCell", Map.of("id", "b", "v", 2)).block();

        StepVerifier.create(store.list("listCell").collectList())
                .assertNext(items -> assertEquals(2, items.size()))
                .verifyComplete();
    }

    @Test
    @DisplayName("update overwrites existing item")
    void update() {
        store.create("upCell", Map.of("id", "u1", "name", "old")).block();

        StepVerifier.create(store.update("upCell", "u1", Map.of("name", "new")))
                .assertNext(updated -> assertEquals("new", updated.get("name")))
                .verifyComplete();
    }

    @Test
    @DisplayName("update returns empty for non-existent item")
    void updateMissing() {
        StepVerifier.create(store.update("upCell", "missing", Map.of("name", "x")))
                .verifyComplete(); // empty mono
    }

    @Test
    @DisplayName("delete returns true for existing, false for missing")
    void delete() {
        store.create("delCell", Map.of("id", "d1")).block();

        StepVerifier.create(store.delete("delCell", "d1"))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(store.delete("delCell", "d1"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("get returns empty for non-existent item")
    void getMissing() {
        StepVerifier.create(store.get("noCell", "no-id"))
                .verifyComplete(); // empty mono
    }
}
