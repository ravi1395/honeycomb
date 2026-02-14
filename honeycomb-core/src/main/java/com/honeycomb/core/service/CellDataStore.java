package com.honeycomb.core.service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Reactive CRUD contract for per-cell data storage.
 *
 * <p>Implementations include in-memory, Redis, and Hibernate Reactive
 * backends. The active implementation per cell is selected by
 * {@link CellDataStoreRouter} based on YAML configuration.</p>
 *
 * @see InMemoryCellDataStore
 * @see RedisCellDataStore
 * @see HibernateReactiveCellDataStore
 * @see CellDataStoreRouter
 */
public interface CellDataStore {
    Flux<Map<String,Object>> list(String cell);

    Mono<Map<String,Object>> get(String cell, String id);

    Mono<Map<String,Object>> create(String cell, Map<String,Object> payload);

    Mono<Map<String,Object>> update(String cell, String id, Map<String,Object> payload);

    Mono<Boolean> delete(String cell, String id);
}
