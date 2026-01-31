package com.example.honeycomb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "honeycomb.autoscale", ignoreInvalidFields = true)
public class HoneycombAutoscaleProperties {
    private boolean enabled = false;
    private Duration evaluationInterval = Duration.ofSeconds(30);
    private double scaleUpRps = 5.0;
    private double scaleDownRps = 0.5;
    private Map<String, Boolean> perCellEnabled = new HashMap<>();

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
        map.put("*", enabled);
        this.perCellEnabled = map;
    }

    public boolean isCellEnabled(String cellName) {
        if (cellName == null) return enabled;
        Boolean value = perCellEnabled.get(cellName);
        if (value != null) return value;
        Boolean fallback = perCellEnabled.get("*");
        if (fallback == null) fallback = perCellEnabled.get("__all__");
        return fallback == null ? enabled : fallback;
    }
}
