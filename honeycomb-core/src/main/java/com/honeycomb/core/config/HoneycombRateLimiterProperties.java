package com.honeycomb.core.config;

import com.honeycomb.core.util.HoneycombConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Rate-limiter configuration properties bound to {@code honeycomb.rate-limiter.*}.
 *
 * <p>Defines global defaults and optional per-cell and per-tenant overrides for
 * request rate limits enforced by {@link com.honeycomb.core.web.RateLimitFilter}.</p>
 *
 * <p><b>v1.5.0:</b> Added per-tenant rate limiting support via {@code per-tenant}
 * map keyed by tenant ID. When multi-tenancy is enabled, requests are first
 * resolved by tenant, then by cell, then by global defaults.</p>
 */
@ConfigurationProperties(prefix = HoneycombConstants.ConfigKeys.RATE_LIMITER_PREFIX)
public class HoneycombRateLimiterProperties {
    private boolean enabled = true;
    private RateLimitConfig defaults = new RateLimitConfig();
    private Map<String, RateLimitConfig> perCell = new HashMap<>();

    /** Per-tenant rate limit overrides. Key = tenant ID, value = rate limit config. */
    private Map<String, RateLimitConfig> perTenant = new HashMap<>();

    /** Whether per-tenant rate limiting is active (requires multi-tenancy enabled). */
    private boolean tenantAware = false;

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
        RateLimitConfig fallback = perCell.get(HoneycombConstants.ConfigKeys.GLOBAL_WILDCARD);
        if (fallback == null) fallback = perCell.get(HoneycombConstants.ConfigKeys.GLOBAL_ALL);
        return fallback == null ? defaults : fallback;
    }

    /**
     * Resolves the rate limit config for a specific tenant and cell combination.
     * Priority: per-tenant → per-cell → wildcard → defaults.
     *
     * @param tenantId the tenant identifier (may be null)
     * @param cellName the cell name (may be null)
     * @return the resolved rate limit configuration
     * @since 1.5.0
     */
    public RateLimitConfig resolveForTenant(String tenantId, String cellName) {
        if (tenantAware && tenantId != null && !tenantId.isBlank()) {
            RateLimitConfig tenantCfg = perTenant.get(tenantId);
            if (tenantCfg != null) return tenantCfg;
            // Fallback: check wildcard tenant
            RateLimitConfig wildcardTenant = perTenant.get(HoneycombConstants.ConfigKeys.GLOBAL_WILDCARD);
            if (wildcardTenant != null) return wildcardTenant;
        }
        return resolve(cellName);
    }

    public Map<String, RateLimitConfig> getPerTenant() {
        return perTenant;
    }

    public void setPerTenant(Map<String, RateLimitConfig> perTenant) {
        this.perTenant = perTenant;
    }

    public boolean isTenantAware() {
        return tenantAware;
    }

    public void setTenantAware(boolean tenantAware) {
        this.tenantAware = tenantAware;
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
