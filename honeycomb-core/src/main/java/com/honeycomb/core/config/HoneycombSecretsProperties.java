package com.honeycomb.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Honeycomb secrets management,
 * bound to {@code honeycomb.secrets.*}.
 *
 * <p>Supports integration with HashiCorp Vault via Spring Cloud Vault.
 * When enabled, sensitive configuration values (API keys, database credentials,
 * encryption keys) are automatically fetched from Vault at startup.</p>
 *
 * @since 1.5.0
 */
@ConfigurationProperties(prefix = "honeycomb.secrets")
public class HoneycombSecretsProperties {

    /** Master switch for secrets management integration. */
    private boolean enabled = false;

    /** Secrets provider: "vault", "aws-secrets-manager", "env". */
    private String provider = "vault";

    /** Vault KV secrets engine path. */
    private String vaultPath = "secret/honeycomb";

    /** Whether to fail startup if Vault is unreachable. */
    private boolean failOnMissing = false;

    /** Refresh interval for secrets rotation (e.g. "5m", "1h"). Set to 0 to disable. */
    private String refreshInterval = "0";

    /** Vault role for AppRole authentication. */
    private String vaultRole = "honeycomb";

    // -- getters / setters --------------------------------------------------

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getVaultPath() { return vaultPath; }
    public void setVaultPath(String vaultPath) { this.vaultPath = vaultPath; }

    public boolean isFailOnMissing() { return failOnMissing; }
    public void setFailOnMissing(boolean failOnMissing) { this.failOnMissing = failOnMissing; }

    public String getRefreshInterval() { return refreshInterval; }
    public void setRefreshInterval(String refreshInterval) { this.refreshInterval = refreshInterval; }

    public String getVaultRole() { return vaultRole; }
    public void setVaultRole(String vaultRole) { this.vaultRole = vaultRole; }
}
