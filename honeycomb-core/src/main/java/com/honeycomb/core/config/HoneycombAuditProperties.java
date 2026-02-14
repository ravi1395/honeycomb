package com.honeycomb.core.config;

import com.honeycomb.core.util.HoneycombConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Audit configuration properties bound to {@code honeycomb.audit.*}.
 *
 * <p>Controls the maximum number of audit entries retained in memory
 * by {@link com.honeycomb.core.service.AuditLogService}.</p>
 */
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
