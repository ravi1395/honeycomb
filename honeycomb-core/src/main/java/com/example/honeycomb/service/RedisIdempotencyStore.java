package com.example.honeycomb.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import com.example.honeycomb.util.HoneycombConstants;

import java.time.Duration;
import java.util.Map;

@SuppressWarnings("null")
public class RedisIdempotencyStore implements IdempotencyStore {
    private static final TypeReference<Map<String,Object>> MAP_TYPE = new TypeReference<>() {};

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;

    public RedisIdempotencyStore(ReactiveStringRedisTemplate redisTemplate, ObjectMapper objectMapper, String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix == null || keyPrefix.isBlank() ? HoneycombConstants.KeyPrefixes.IDEMPOTENCY : keyPrefix;
    }

    @Override
    public Mono<ResponseEntity<Map<String,Object>>> get(@NonNull String key) {
        String redisKey = key(key);
        return redisTemplate.opsForValue().get(redisKey)
                .flatMap(this::deserialize);
    }

    @Override
    public Mono<Void> put(@NonNull String key, ResponseEntity<Map<String,Object>> response, long ttlSeconds) {
        if (response == null || response.getBody() == null) {
            return Mono.empty();
        }
        String redisKey = key(key);
        Duration ttl = Duration.ofSeconds(Math.max(1, ttlSeconds));
        return serialize(response)
                .flatMap(json -> redisTemplate.opsForValue().set(redisKey, json, ttl))
                .then();
    }

    @Override
    public String type() {
        return HoneycombConstants.Names.STORE_REDIS;
    }

    private @NonNull String key(@NonNull String key) {
        return keyPrefix + HoneycombConstants.Names.SEPARATOR_COLON + key;
    }

    private Mono<ResponseEntity<Map<String,Object>>> deserialize(String json) {
        if (json == null || json.isBlank()) return Mono.empty();
        return Mono.fromCallable(() -> {
            Map<String,Object> payload = objectMapper.readValue(json, MAP_TYPE);
            Object statusObj = payload.getOrDefault(HoneycombConstants.JsonKeys.STATUS, 200);
            int status = statusObj instanceof Number n ? n.intValue() : Integer.parseInt(statusObj.toString());
            Object bodyObj = payload.get(HoneycombConstants.JsonKeys.BODY);
            Map<String,Object> body = bodyObj instanceof Map<?,?> m ? safeMap(m) : Map.of();
            return ResponseEntity.status(status).body(body);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<String> serialize(ResponseEntity<Map<String,Object>> response) {
        return Mono.fromCallable(() -> {
            Map<String,Object> wrapper = new java.util.HashMap<>();
            wrapper.put(HoneycombConstants.JsonKeys.STATUS, response.getStatusCode().value());
            wrapper.put(HoneycombConstants.JsonKeys.BODY, response.getBody());
            return objectMapper.writeValueAsString(wrapper);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Map<String,Object> safeMap(Map<?,?> input) {
        Map<String,Object> out = new java.util.HashMap<>();
        for (Map.Entry<?,?> e : input.entrySet()) {
            if (e.getKey() != null) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return out;
    }
}
