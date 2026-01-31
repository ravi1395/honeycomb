package com.example.honeycomb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "honeycomb.security")
public class HoneycombSecurityProperties {
    private ApiKeys apiKeys = new ApiKeys();

    public ApiKeys getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(ApiKeys apiKeys) {
        this.apiKeys = apiKeys;
    }

    public static class ApiKeys {
        private boolean enabled = false;
        private String header = "X-API-Key";
        private Map<String, String> keys = new HashMap<>();
        private Map<String, List<String>> perCell = new HashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHeader() {
            return header;
        }

        public void setHeader(String header) {
            this.header = header;
        }

        public Map<String, String> getKeys() {
            return keys;
        }

        public void setKeys(Map<String, String> keys) {
            this.keys = keys;
        }

        public Map<String, List<String>> getPerCell() {
            return perCell;
        }

        public void setPerCell(Map<String, List<String>> perCell) {
            this.perCell = perCell;
        }

        public List<String> resolveAllowedKeys(String cellName) {
            if (cellName == null || cellName.isBlank()) {
                return List.of();
            }
            List<String> direct = perCell.get(cellName);
            if (direct != null && !direct.isEmpty()) {
                return direct;
            }
            List<String> fallback = perCell.get("*");
            if (fallback == null) fallback = perCell.get("__all__");
            return fallback == null ? List.of() : fallback;
        }

        public boolean isKnownKey(String value) {
            if (value == null || value.isBlank()) return false;
            return keys.values().stream().anyMatch(value::equals);
        }

        public String resolveKeyName(String value) {
            if (value == null) return "";
            return keys.entrySet().stream()
                    .filter(e -> value.equals(e.getValue()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse("unknown");
        }

        public List<String> allKeys() {
            return new ArrayList<>(keys.values());
        }
    }
}
