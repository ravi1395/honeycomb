package com.example.honeycomb.example.service;

import com.example.honeycomb.annotations.Cell;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Cell("CatalogService")
public class CatalogServiceCell implements CatalogServiceApi {
    private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    public List<Map<String, Object>> listItems() {
        return new ArrayList<>(store.values());
    }

    public Map<String, Object> getItem(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id-required");
        }
        Map<String, Object> item = store.get(id);
        if (item == null) {
            throw new IllegalArgumentException("not-found");
        }
        return item;
    }

    public Map<String, Object> createItem(Map<String, Object> body) {
        String id = idFrom(body);
        Map<String, Object> copy = new LinkedHashMap<>(body);
        copy.put("id", id);
        store.put(id, copy);
        return copy;
    }

    public Map<String, Object> updateItem(Map<String, Object> body) {
        String id = idFrom(body);
        Map<String, Object> existing = store.get(id);
        if (existing == null) {
            throw new IllegalArgumentException("not-found");
        }
        existing.putAll(body);
        existing.put("id", id);
        return existing;
    }

    public Map<String, Object> deleteItem(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id-required");
        }
        Map<String, Object> removed = store.remove(id);
        if (removed == null) {
            throw new IllegalArgumentException("not-found");
        }
        return Map.of("deleted", true, "id", id);
    }

    private String idFrom(Map<String, Object> body) {
        if (body == null) throw new IllegalArgumentException("body-required");
        Object idVal = body.get("id");
        String id = idVal == null ? null : idVal.toString();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id-required");
        }
        return id;
    }
}
