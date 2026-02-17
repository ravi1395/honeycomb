package com.honeycomb.core.tenant;

import com.honeycomb.core.config.HoneycombTenantProperties;
import com.honeycomb.core.service.CellDataStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration that wraps the active {@link CellDataStore} with
 * a {@link TenantAwareCellDataStore} decorator when multi-tenancy
 * is enabled.
 *
 * @since 1.4.3
 */
@Configuration
@ConditionalOnProperty(name = "honeycomb.tenant.enabled", havingValue = "true")
public class TenantDataStoreConfig {

    @Bean
    @Primary
    public CellDataStore tenantAwareCellDataStore(CellDataStore delegate,
                                                   HoneycombTenantProperties props) {
        return new TenantAwareCellDataStore(delegate);
    }
}
