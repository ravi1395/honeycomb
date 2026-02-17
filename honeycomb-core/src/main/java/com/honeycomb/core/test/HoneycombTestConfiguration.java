package com.honeycomb.core.test;

import com.honeycomb.core.service.CellDataStore;
import com.honeycomb.core.service.CellRegistry;
import com.honeycomb.core.service.InMemoryCellDataStore;
import com.honeycomb.core.service.SharedwallMethodCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * {@link TestConfiguration} that provides in-memory beans for {@link HoneycombTest}.
 *
 * <p>Registers an {@link InMemoryCellDataStore} if no other {@link CellDataStore}
 * bean is present, ensuring tests run without external infrastructure.</p>
 *
 * @since 1.4.3
 */
@TestConfiguration
public class HoneycombTestConfiguration {

    @Bean
    @ConditionalOnMissingBean(CellDataStore.class)
    public CellDataStore testCellDataStore() {
        return new InMemoryCellDataStore();
    }
}
