package com.example.honeycomb.service;

import com.example.honeycomb.util.HoneycombConstants;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryIdempotencyStore implements IdempotencyStore {
    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    @Override
    public Mono<ResponseEntity<Map<String,Object>>> get(@NonNull String key) {
        cleanupExpired();
        Entry entry = cache.get(key);
        if (entry == null || entry.isExpired()) {
            return Mono.empty();
        }
        return Mono.just(entry.toResponse());
    }

    @Override
    public Mono<Void> put(@NonNull String key, ResponseEntity<Map<String,Object>> response, long ttlSeconds) {
        if (response == null || response.getBody() == null) {
            return Mono.empty();
        }
        cache.put(key, Entry.from(response, ttlSeconds));
        return Mono.empty();
    }

    @Override
    public String type() {
        return HoneycombConstants.Names.STORE_MEMORY;
    }

    private void cleanupExpired() {
        for (Map.Entry<String, Entry> entry : cache.entrySet()) {
            if (entry.getValue().isExpired()) {
                cache.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private static final class Entry {
        private final Map<String,Object> body;
        private final int status;
        private final Instant expiresAt;

        private Entry(Map<String,Object> body, int status, Instant expiresAt) {
            this.body = body;
            this.status = status;
            this.expiresAt = expiresAt;
        }

        static Entry from(ResponseEntity<Map<String,Object>> response, long ttlSeconds) {
            Instant expiresAt = Instant.now().plusSeconds(Math.max(1, ttlSeconds));
            return new Entry(response.getBody(), response.getStatusCode().value(), expiresAt);
        }

        boolean isExpired() {
            return expiresAt.isBefore(Instant.now());
        }

        ResponseEntity<Map<String,Object>> toResponse() {
            return ResponseEntity.status(status).body(body);
        }
    }
}
