package com.honeycomb.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for Honeycomb multi-tenancy support,
 * bound to {@code honeycomb.tenant.*}.
 *
 * <p>When enabled, every request must carry an {@code X-Tenant-Id} header
 * (configurable). Storage, metrics, and audit logs are automatically
 * scoped to the resolved tenant.</p>
 *
 * @since 1.4.3
 */
@ConfigurationProperties(prefix = "honeycomb.tenant")
public class HoneycombTenantProperties {

    /** Master switch for multi-tenancy. */
    private boolean enabled = false;

    /** HTTP header name used to resolve the tenant identifier. */
    private String headerName = "X-Tenant-Id";

    /** Default tenant ID used when the header is absent (empty = reject). */
    private String defaultTenant = "";

    /** Known tenant IDs for validation (empty = accept any). */
    private List<String> allowedTenants = new ArrayList<>();

    /** Whether to enforce that every request carries a tenant header. */
    private boolean enforceHeader = true;

    /** Key prefix template for tenant-scoped Redis storage. {@code {tenant}} is replaced. */
    private String storageKeyTemplate = "honeycomb:tenant:{tenant}:cell";

    /** Whether to add tenant tag to all Micrometer metrics. */
    private boolean scopeMetrics = true;

    // -- getters / setters --------------------------------------------------

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getHeaderName() { return headerName; }
    public void setHeaderName(String headerName) { this.headerName = headerName; }

    public String getDefaultTenant() { return defaultTenant; }
    public void setDefaultTenant(String defaultTenant) { this.defaultTenant = defaultTenant; }

    public List<String> getAllowedTenants() { return allowedTenants; }
    public void setAllowedTenants(List<String> allowedTenants) { this.allowedTenants = allowedTenants; }

    public boolean isEnforceHeader() { return enforceHeader; }
    public void setEnforceHeader(boolean enforceHeader) { this.enforceHeader = enforceHeader; }

    public String getStorageKeyTemplate() { return storageKeyTemplate; }
    public void setStorageKeyTemplate(String storageKeyTemplate) { this.storageKeyTemplate = storageKeyTemplate; }

    public boolean isScopeMetrics() { return scopeMetrics; }
    public void setScopeMetrics(boolean scopeMetrics) { this.scopeMetrics = scopeMetrics; }
}
