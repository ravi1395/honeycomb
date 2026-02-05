package com.example.honeycomb.service;

import com.example.honeycomb.config.HoneycombStorageProperties;
import com.example.honeycomb.util.HoneycombConstants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class RedisCellDataStore implements CellDataStore {
    private static final TypeReference<Map<String,Object>> MAP_TYPE = new TypeReference<>() {};

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;

    public RedisCellDataStore(ReactiveStringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this(redisTemplate, objectMapper, new HoneycombStorageProperties());
    }

    public RedisCellDataStore(ReactiveStringRedisTemplate redisTemplate, ObjectMapper objectMapper, HoneycombStorageProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.keyPrefix = properties != null && properties.getKeyPrefix() != null && !properties.getKeyPrefix().isBlank()
                ? properties.getKeyPrefix()
            : HoneycombConstants.KeyPrefixes.CELL;
    }

    @Override
    public Flux<Map<String,Object>> list(String cell) {
        String pattern = keyPrefix
            + HoneycombConstants.Names.SEPARATOR_COLON
            + cell
            + HoneycombConstants.Names.SEPARATOR_COLON
            + HoneycombConstants.ConfigKeys.GLOBAL_WILDCARD;
        return redisTemplate.keys(pattern)
                .flatMap(key -> redisTemplate.opsForValue().get(key))
                .flatMap(this::deserialize);
    }

    @Override
    public Mono<Map<String,Object>> get(String cell, String id) {
        return redisTemplate.opsForValue().get(key(cell, id))
                .flatMap(this::deserialize);
    }

    @Override
    public Mono<Map<String,Object>> create(String cell, Map<String,Object> payload) {
        String id = Optional.ofNullable(payload).map(p -> (String) p.get(HoneycombConstants.JsonKeys.ID))
                .orElse(UUID.randomUUID().toString());
        return serializePayload(id, payload)
                .flatMap(json -> redisTemplate.opsForValue().set(key(cell, id), json).thenReturn(json))
                .flatMap(this::deserialize);
    }

    @Override
    public Mono<Map<String,Object>> update(String cell, String id, Map<String,Object> payload) {
        String key = key(cell, id);
        return redisTemplate.opsForValue().get(key)
                .switchIfEmpty(Mono.empty())
                .flatMap(existing -> serializePayload(id, payload)
                        .flatMap(json -> redisTemplate.opsForValue().set(key, json).thenReturn(json))
                        .flatMap(this::deserialize));
    }

    @Override
    public Mono<Boolean> delete(String cell, String id) {
        return redisTemplate.delete(key(cell, id)).map(count -> count != null && count > 0);
    }

    private String key(String cell, String id) {
        return keyPrefix
                + HoneycombConstants.Names.SEPARATOR_COLON
                + cell
                + HoneycombConstants.Names.SEPARATOR_COLON
                + id;
    }

    private Mono<Map<String,Object>> deserialize(String json) {
        if (json == null || json.isBlank()) return Mono.empty();
        return Mono.fromCallable(() -> objectMapper.readValue(json, MAP_TYPE))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<String> serializePayload(String id, Map<String,Object> payload) {
        return Mono.fromCallable(() -> {
            Map<String,Object> copy = payload == null ? Map.of(HoneycombConstants.JsonKeys.ID, id) : new java.util.HashMap<>(payload);
            copy.put(HoneycombConstants.JsonKeys.ID, id);
            return objectMapper.writeValueAsString(copy);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
