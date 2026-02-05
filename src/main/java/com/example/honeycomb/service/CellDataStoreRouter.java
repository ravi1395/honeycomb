package com.example.honeycomb.service;

import com.example.honeycomb.config.HoneycombStorageProperties;
import com.example.honeycomb.util.HoneycombConstants;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Map;

public class CellDataStoreRouter implements CellDataStore {
    private final HoneycombStorageProperties storageProperties;
    private final Map<String, CellDataStore> storesByType;

    public CellDataStoreRouter(HoneycombStorageProperties storageProperties,
                               Map<String, CellDataStore> storesByType) {
        this.storageProperties = storageProperties;
        this.storesByType = storesByType;
    }

    @Override
    public Flux<Map<String, Object>> list(String cell) {
        return resolve(cell).list(cell);
    }

    @Override
    public Mono<Map<String, Object>> get(String cell, String id) {
        return resolve(cell).get(cell, id);
    }

    @Override
    public Mono<Map<String, Object>> create(String cell, Map<String, Object> payload) {
        return resolve(cell).create(cell, payload);
    }

    @Override
    public Mono<Map<String, Object>> update(String cell, String id, Map<String, Object> payload) {
        return resolve(cell).update(cell, id, payload);
    }

    @Override
    public Mono<Boolean> delete(String cell, String id) {
        return resolve(cell).delete(cell, id);
    }

    private CellDataStore resolve(String cell) {
        String type = null;
        if (storageProperties.getRouting() != null && storageProperties.getRouting().getPerCell() != null) {
            type = storageProperties.getRouting().getPerCell().get(cell);
        }
        if (type == null || type.isBlank()) {
            type = storageProperties.getType();
        }
        if (type == null || type.isBlank()) {
            type = HoneycombConstants.Names.STORE_MEMORY;
        }
        CellDataStore store = storesByType.get(type.toLowerCase(Locale.ROOT));
        if (store != null) {
            return store;
        }
        CellDataStore fallback = storesByType.get(HoneycombConstants.Names.STORE_MEMORY);
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException(HoneycombConstants.Messages.CELL_DATASTORE_MISSING + type);
    }
}
