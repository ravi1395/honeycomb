package com.example.honeycomb.config;

import com.example.honeycomb.util.HoneycombConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = HoneycombConstants.ConfigKeys.IDEMPOTENCY_PREFIX, ignoreInvalidFields = true)
public class HoneycombIdempotencyProperties {
    /**
     * Enable idempotency handling for create/update requests.
     */
    private boolean enabled = false;

    /**
     * Header used to supply an idempotency key.
     */
    private String header = HoneycombConstants.Headers.IDEMPOTENCY_KEY;

    /**
     * TTL in seconds for idempotency entries.
     */
    private long ttlSeconds = 300;

    /**
     * Store type: memory | redis
     */
    private String store = HoneycombConstants.Names.STORE_MEMORY;

    /**
     * Key prefix used by Redis store.
     */
    private String keyPrefix = com.example.honeycomb.util.HoneycombConstants.KeyPrefixes.IDEMPOTENCY;

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

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }
}
