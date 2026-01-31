package com.example.honeycomb.service;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CellDataStore {
    // cellName -> (id -> object map)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Map<String, Object>>> store = new ConcurrentHashMap<>();

    private ConcurrentHashMap<String, Map<String,Object>> mapFor(String cell) {
        return store.computeIfAbsent(cell, d -> new ConcurrentHashMap<>());
    }

    public Flux<Map<String,Object>> list(String cell) {
        return Flux.fromIterable(mapFor(cell).values());
    }

    public Mono<Map<String,Object>> get(String cell, String id) {
        Map<String,Object> v = mapFor(cell).get(id);
        return v == null ? Mono.empty() : Mono.just(v);
    }

    public Mono<Map<String,Object>> create(String cell, Map<String,Object> payload) {
        String id = Optional.ofNullable(payload).map(p -> (String) p.get("id"))
                .orElse(UUID.randomUUID().toString());
        Map<String,Object> copy = normalizedPayload(id, payload);
        mapFor(cell).put(id, copy);
        return Mono.just(copy);
    }

    public Mono<Map<String,Object>> update(String cell, String id, Map<String,Object> payload) {
        var map = mapFor(cell);
        if (!map.containsKey(id)) return Mono.empty();
        Map<String,Object> copy = normalizedPayload(id, payload);
        map.put(id, copy);
        return Mono.just(copy);
    }

    public Mono<Boolean> delete(String cell, String id) {
        var map = mapFor(cell);
        return Mono.just(map.remove(id) != null);
    }

    private Map<String,Object> normalizedPayload(String id, Map<String,Object> payload) {
        int initialCapacity = payload == null ? 1 : Math.max(1, payload.size() + 1);
        Map<String,Object> copy = new HashMap<>(initialCapacity);
        if (payload != null) {
            copy.putAll(payload);
        }
        copy.put("id", id);
        return Collections.unmodifiableMap(copy);
    }
}
