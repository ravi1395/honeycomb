package com.example.honeycomb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "honeycomb.rate-limiter")
public class HoneycombRateLimiterProperties {
    private boolean enabled = true;
    private RateLimitConfig defaults = new RateLimitConfig();
    private Map<String, RateLimitConfig> perCell = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public RateLimitConfig getDefaults() {
        return defaults;
    }

    public void setDefaults(RateLimitConfig defaults) {
        this.defaults = defaults;
    }

    public Map<String, RateLimitConfig> getPerCell() {
        return perCell;
    }

    public void setPerCell(Map<String, RateLimitConfig> perCell) {
        this.perCell = perCell;
    }

    public RateLimitConfig resolve(String cellName) {
        if (cellName == null) return defaults;
        RateLimitConfig cfg = perCell.get(cellName);
        if (cfg != null) return cfg;
        RateLimitConfig fallback = perCell.get("*");
        if (fallback == null) fallback = perCell.get("__all__");
        return fallback == null ? defaults : fallback;
    }

    public static class RateLimitConfig {
        private int limitForPeriod = 50;
        private Duration refreshPeriod = Duration.ofSeconds(1);
        private Duration timeout = Duration.ofMillis(0);

        public int getLimitForPeriod() {
            return limitForPeriod;
        }

        public void setLimitForPeriod(int limitForPeriod) {
            this.limitForPeriod = limitForPeriod;
        }

        public Duration getRefreshPeriod() {
            return refreshPeriod;
        }

        public void setRefreshPeriod(Duration refreshPeriod) {
            this.refreshPeriod = refreshPeriod;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }
}
