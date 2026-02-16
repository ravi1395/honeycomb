package com.honeycomb.core.config;

import com.honeycomb.core.util.HoneycombConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for cell versioning and blue-green / canary dispatch.
 *
 * <p>Bound to {@code honeycomb.versioning.*}.</p>
 *
 * <h3>Example configuration</h3>
 * <pre>{@code
 * honeycomb:
 *   versioning:
 *     enabled: true
 *     default-version: v1
 *     traffic-split:
 *       catalog:
 *         v1: 90
 *         v2: 10
 *       inventory:
 *         v1: 0
 *         v2: 100
 * }</pre>
 *
 * @since 1.4.2
 * @see com.honeycomb.core.service.CellVersioningService
 */
@ConfigurationProperties(prefix = "honeycomb.versioning")
public class HoneycombVersioningProperties {

    /** Whether cell versioning & blue-green dispatch is enabled. */
    private boolean enabled = false;

    /** The default version to use when none is specified in a request. */
    private String defaultVersion = "v1";

    /**
     * Per-cell traffic split configuration.
     * <p>Outer key = cell name, inner map = version → weight (0–100).
     * Weights do not need to sum to 100; they are normalised at runtime.</p>
     */
    private Map<String, Map<String, Integer>> trafficSplit = new HashMap<>();

    /**
     * Header name used to request a specific cell version explicitly.
     * Overrides the traffic split when present.
     */
    private String versionHeader = "X-Cell-Version";

    // ----- getters / setters -------------------------------------------------

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultVersion() {
        return defaultVersion;
    }

    public void setDefaultVersion(String defaultVersion) {
        this.defaultVersion = defaultVersion;
    }

    public Map<String, Map<String, Integer>> getTrafficSplit() {
        return trafficSplit;
    }

    public void setTrafficSplit(Map<String, Map<String, Integer>> trafficSplit) {
        this.trafficSplit = trafficSplit == null ? new HashMap<>() : trafficSplit;
    }

    public String getVersionHeader() {
        return versionHeader;
    }

    public void setVersionHeader(String versionHeader) {
        this.versionHeader = versionHeader;
    }

    // ----- helpers -----------------------------------------------------------

    /**
     * Resolve the traffic-split weights for a given cell.
     *
     * @param cellName the cell name
     * @return version→weight map; empty map if no split is configured
     */
    public Map<String, Integer> resolveTrafficSplit(String cellName) {
        if (cellName == null || trafficSplit == null) return Map.of();
        Map<String, Integer> cellSplit = trafficSplit.get(cellName);
        if (cellSplit != null) return cellSplit;
        // global wildcard fallback
        Map<String, Integer> wildcard = trafficSplit.get("*");
        return wildcard != null ? wildcard : Map.of();
    }
}
