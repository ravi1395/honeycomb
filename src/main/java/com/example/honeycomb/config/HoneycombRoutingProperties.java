package com.example.honeycomb.config;

import com.example.honeycomb.util.HoneycombConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = HoneycombConstants.ConfigKeys.ROUTING_PREFIX, ignoreInvalidFields = true)
public class HoneycombRoutingProperties {
    private String defaultPolicy = HoneycombConstants.RoutingPolicies.ALL;
    private Map<String, String> perCellPolicy = new HashMap<>();
    private Map<String, Map<String, Integer>> weights = new HashMap<>();

    public String getDefaultPolicy() {
        return defaultPolicy;
    }

    public void setDefaultPolicy(String defaultPolicy) {
        this.defaultPolicy = defaultPolicy;
    }

    public Map<String, String> getPerCellPolicy() {
        return perCellPolicy;
    }

    public void setPerCellPolicy(Map<String, String> perCellPolicy) {
        this.perCellPolicy = perCellPolicy == null ? new HashMap<>() : perCellPolicy;
    }

    public Map<String, Map<String, Integer>> getWeights() {
        return weights;
    }

    public void setWeights(Map<String, Map<String, Integer>> weights) {
        this.weights = weights;
    }

    public String resolvePolicy(String cellName) {
        if (cellName == null) return defaultPolicy;
        String policy = perCellPolicy.get(cellName);
        if (policy != null && !policy.isBlank()) return policy;
        String fallback = perCellPolicy.get(HoneycombConstants.ConfigKeys.GLOBAL_WILDCARD);
        if (fallback == null) fallback = perCellPolicy.get(HoneycombConstants.ConfigKeys.GLOBAL_ALL);
        return (fallback == null || fallback.isBlank()) ? defaultPolicy : fallback;
    }

    public Map<String, Integer> resolveWeights(String cellName) {
        if (cellName == null) return Map.of();
        Map<String, Integer> map = weights.get(cellName);
        if (map != null) return map;
        Map<String, Integer> fallback = weights.get(HoneycombConstants.ConfigKeys.GLOBAL_WILDCARD);
        if (fallback == null) fallback = weights.get(HoneycombConstants.ConfigKeys.GLOBAL_ALL);
        return fallback == null ? Map.of() : fallback;
    }
}
