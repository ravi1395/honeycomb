package com.example.honeycomb.config;

import com.example.honeycomb.util.HoneycombConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = HoneycombConstants.ConfigKeys.AUTOSCALE_PREFIX, ignoreInvalidFields = true)
public class HoneycombAutoscaleProperties {
    private boolean enabled = false;
    private Duration evaluationInterval = Duration.ofSeconds(30);
    private double scaleUpRps = 5.0;
    private double scaleDownRps = 0.5;
    private Map<String, Boolean> perCellEnabled = new HashMap<>();
    private Map<String, Double> perCellScaleUpRps = new HashMap<>();
    private Map<String, Double> perCellScaleDownRps = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getEvaluationInterval() {
        return evaluationInterval;
    }

    public void setEvaluationInterval(Duration evaluationInterval) {
        this.evaluationInterval = evaluationInterval;
    }

    public double getScaleUpRps() {
        return scaleUpRps;
    }

    public void setScaleUpRps(double scaleUpRps) {
        this.scaleUpRps = scaleUpRps;
    }

    public double getScaleDownRps() {
        return scaleDownRps;
    }

    public void setScaleDownRps(double scaleDownRps) {
        this.scaleDownRps = scaleDownRps;
    }

    public Map<String, Boolean> getPerCellEnabled() {
        return perCellEnabled;
    }

    public void setPerCellEnabled(Map<String, Boolean> perCellEnabled) {
        this.perCellEnabled = perCellEnabled == null ? new HashMap<>() : perCellEnabled;
    }

    public void setPerCellEnabled(Boolean enabled) {
        if (enabled == null) return;
        Map<String, Boolean> map = new HashMap<>();
        map.put(HoneycombConstants.ConfigKeys.GLOBAL_WILDCARD, enabled);
        this.perCellEnabled = map;
    }

    public Map<String, Double> getPerCellScaleUpRps() {
        return perCellScaleUpRps;
    }

    public void setPerCellScaleUpRps(Map<String, Double> perCellScaleUpRps) {
        this.perCellScaleUpRps = perCellScaleUpRps == null ? new HashMap<>() : perCellScaleUpRps;
    }

    public Map<String, Double> getPerCellScaleDownRps() {
        return perCellScaleDownRps;
    }

    public void setPerCellScaleDownRps(Map<String, Double> perCellScaleDownRps) {
        this.perCellScaleDownRps = perCellScaleDownRps == null ? new HashMap<>() : perCellScaleDownRps;
    }

    public boolean isCellEnabled(String cellName) {
        if (cellName == null) return enabled;
        Boolean value = perCellEnabled.get(cellName);
        if (value != null) return value;
        Boolean fallback = perCellEnabled.get(HoneycombConstants.ConfigKeys.GLOBAL_WILDCARD);
        if (fallback == null) fallback = perCellEnabled.get(HoneycombConstants.ConfigKeys.GLOBAL_ALL);
        return fallback == null ? enabled : fallback;
    }

    public double resolveScaleUpRps(String cellName) {
        return resolvePerCellDouble(cellName, perCellScaleUpRps, scaleUpRps);
    }

    public double resolveScaleDownRps(String cellName) {
        return resolvePerCellDouble(cellName, perCellScaleDownRps, scaleDownRps);
    }

    private double resolvePerCellDouble(String cellName, Map<String, Double> map, double fallbackValue) {
        if (cellName == null) return fallbackValue;
        Double value = map.get(cellName);
        if (value != null) return value;
        Double fallback = map.get(HoneycombConstants.ConfigKeys.GLOBAL_WILDCARD);
        if (fallback == null) fallback = map.get(HoneycombConstants.ConfigKeys.GLOBAL_ALL);
        return fallback == null ? fallbackValue : fallback;
    }
}
