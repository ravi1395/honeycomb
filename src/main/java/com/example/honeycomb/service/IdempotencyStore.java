package com.example.honeycomb.service;

import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface IdempotencyStore {
    Mono<ResponseEntity<Map<String,Object>>> get(@NonNull String key);

    Mono<Void> put(@NonNull String key, ResponseEntity<Map<String,Object>> response, long ttlSeconds);

    String type();
}
