package com.example.honeycomb.service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface CellDataStore {
    Flux<Map<String,Object>> list(String cell);

    Mono<Map<String,Object>> get(String cell, String id);

    Mono<Map<String,Object>> create(String cell, Map<String,Object> payload);

    Mono<Map<String,Object>> update(String cell, String id, Map<String,Object> payload);

    Mono<Boolean> delete(String cell, String id);
}
