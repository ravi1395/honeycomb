package com.example.honeycomb.service;

import com.example.honeycomb.config.HoneycombIdempotencyProperties;
import com.example.honeycomb.util.HoneycombConstants;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Map;

@Component
public class IdempotencyService {
    private final HoneycombIdempotencyProperties properties;
    private final ObjectProvider<IdempotencyStore> storeProvider;

    public IdempotencyService(HoneycombIdempotencyProperties properties,
                              ObjectProvider<IdempotencyStore> storeProvider) {
        this.properties = properties;
        this.storeProvider = storeProvider;
    }

    public Mono<ResponseEntity<Map<String,Object>>> handle(String key,
                                                           Mono<ResponseEntity<Map<String,Object>>> action) {
        if (!properties.isEnabled() || key == null || key.isBlank()) {
            return action;
        }
        IdempotencyStore store = resolveStore();
        if (store == null) {
            return action;
        }
        return store.get(key)
                .switchIfEmpty(action.doOnNext(response -> store.put(key, response, properties.getTtlSeconds()).subscribe()));
    }

    private IdempotencyStore resolveStore() {
        String type = properties.getStore() == null
            ? HoneycombConstants.Names.STORE_MEMORY
            : properties.getStore().toLowerCase(Locale.ROOT);
        for (IdempotencyStore store : storeProvider) {
            if (store.type().equalsIgnoreCase(type)) {
                return store;
            }
        }
        return null;
    }
}
