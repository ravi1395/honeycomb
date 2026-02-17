package com.honeycomb.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration that activates Spring Cloud Vault integration when available.
 *
 * <p>This configuration is activated when:
 * <ol>
 *   <li>{@code honeycomb.secrets.enabled=true}</li>
 *   <li>Spring Cloud Vault is on the classpath</li>
 * </ol>
 *
 * <p>Vault connection settings are configured via standard Spring Cloud Vault
 * properties ({@code spring.cloud.vault.*}), which can themselves be sourced
 * from environment variables or bootstrap configuration.</p>
 *
 * @since 1.5.0
 * @see HoneycombSecretsProperties
 */
@Configuration
@ConditionalOnProperty(name = "honeycomb.secrets.enabled", havingValue = "true")
@ConditionalOnClass(name = "org.springframework.cloud.vault.config.VaultConfigurer")
public class VaultSecretsConfig {

    private static final Logger log = LoggerFactory.getLogger(VaultSecretsConfig.class);

    public VaultSecretsConfig(HoneycombSecretsProperties secretsProperties) {
        log.info("Honeycomb secrets management activated: provider={}, path={}",
                secretsProperties.getProvider(), secretsProperties.getVaultPath());
    }
}
