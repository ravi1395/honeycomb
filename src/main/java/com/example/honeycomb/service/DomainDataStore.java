package com.example.honeycomb.service;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DomainDataStore {
    // domainName -> (id -> object map)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Map<String, Object>>> store = new ConcurrentHashMap<>();

    private ConcurrentHashMap<String, Map<String,Object>> mapFor(String domain) {
        return store.computeIfAbsent(domain, d -> new ConcurrentHashMap<>());
    }

    public Flux<Map<String,Object>> list(String domain) {
        return Flux.fromIterable(mapFor(domain).values());
    }

    public Mono<Map<String,Object>> get(String domain, String id) {
        Map<String,Object> v = mapFor(domain).get(id);
        return v == null ? Mono.empty() : Mono.just(v);
    }

    public Mono<Map<String,Object>> create(String domain, Map<String,Object> payload) {
        String id = Optional.ofNullable((String) payload.get("id")).orElse(UUID.randomUUID().toString());
        var copy = new java.util.concurrent.ConcurrentHashMap<String, Object>(payload == null ? java.util.Map.of() : payload);
        copy.put("id", id);
        mapFor(domain).put(id, copy);
        return Mono.just(copy);
    }

    public Mono<Map<String,Object>> update(String domain, String id, Map<String,Object> payload) {
        var map = mapFor(domain);
        if (!map.containsKey(id)) return Mono.empty();
        var copy = new java.util.concurrent.ConcurrentHashMap<String, Object>(payload == null ? java.util.Map.of() : payload);
        copy.put("id", id);
        map.put(id, copy);
        return Mono.just(copy);
    }

    public Mono<Boolean> delete(String domain, String id) {
        var map = mapFor(domain);
        return Mono.just(map.remove(id) != null);
    }
}
