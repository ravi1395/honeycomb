package com.honeycomb.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Honeycomb API versioning,
 * bound to {@code honeycomb.api.*}.
 *
 * <p>When enabled, all Honeycomb endpoints are prefixed with a version
 * segment (e.g. {@code /v1/honeycomb/cells}). The old un-versioned
 * paths remain active for backward compatibility unless
 * {@code legacy-paths-enabled} is set to {@code false}.</p>
 *
 * @since 1.5.0
 */
@ConfigurationProperties(prefix = "honeycomb.api")
public class HoneycombApiVersionProperties {

    /** Master switch for API versioning. */
    private boolean versioningEnabled = true;

    /** Current API version identifier (e.g. "v1"). */
    private String currentVersion = "v1";

    /** Whether to keep legacy (un-versioned) paths active alongside versioned paths. */
    private boolean legacyPathsEnabled = true;

    /** Custom header that reports the API version in responses. */
    private String versionHeader = "X-API-Version";

    // -- getters / setters --------------------------------------------------

    public boolean isVersioningEnabled() { return versioningEnabled; }
    public void setVersioningEnabled(boolean versioningEnabled) { this.versioningEnabled = versioningEnabled; }

    public String getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(String currentVersion) { this.currentVersion = currentVersion; }

    public boolean isLegacyPathsEnabled() { return legacyPathsEnabled; }
    public void setLegacyPathsEnabled(boolean legacyPathsEnabled) { this.legacyPathsEnabled = legacyPathsEnabled; }

    public String getVersionHeader() { return versionHeader; }
    public void setVersionHeader(String versionHeader) { this.versionHeader = versionHeader; }

    /**
     * Returns the versioned base path prefix, e.g. {@code "/v1"}.
     */
    public String getVersionPrefix() {
        return "/" + currentVersion;
    }
}
