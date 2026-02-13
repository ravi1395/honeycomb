package com.honeycomb.core.config;

import com.honeycomb.core.util.HoneycombConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = HoneycombConstants.ConfigKeys.AUDIT_PREFIX)
public class HoneycombAuditProperties {
    private int maxEntries = 500;

    public int getMaxEntries() {
        return maxEntries;
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }
}
