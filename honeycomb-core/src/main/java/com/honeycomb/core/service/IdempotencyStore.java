package com.honeycomb.core.service;

import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Contract for storing and retrieving cached idempotent HTTP responses by key.
 *
 * <p>Implementations must honour the configured TTL so stale entries
 * are automatically evicted. Used by {@link IdempotencyService} to
 * prevent duplicate side-effects on retried requests.</p>
 *
 * @see InMemoryIdempotencyStore
 * @see RedisIdempotencyStore
 */
public interface IdempotencyStore {
    Mono<ResponseEntity<Map<String,Object>>> get(@NonNull String key);

    Mono<Void> put(@NonNull String key, ResponseEntity<Map<String,Object>> response, long ttlSeconds);

    String type();
}
