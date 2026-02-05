package com.example.honeycomb.config;

import com.example.honeycomb.service.CellDataStore;
import com.example.honeycomb.service.CellDataStoreRouter;
import com.example.honeycomb.service.InMemoryCellDataStore;
import com.example.honeycomb.service.RedisCellDataStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CellDataStoreConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CellDataStoreConfig.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void defaultsToInMemoryStore() {
        contextRunner.run(context -> {
            CellDataStore store = context.getBean(CellDataStore.class);
            assertThat(store).isInstanceOf(InMemoryCellDataStore.class);
        });
    }

    @Test
    void usesRedisStoreWhenConfigured() {
        contextRunner
                .withPropertyValues("honeycomb.storage.type=redis")
                .withBean(ReactiveStringRedisTemplate.class, () -> mock(ReactiveStringRedisTemplate.class))
                .run(context -> {
                    CellDataStore store = context.getBean(CellDataStore.class);
                    assertThat(store).isInstanceOf(RedisCellDataStore.class);
                });
    }

    @Test
    void usesRouterWhenRoutingEnabled() {
        contextRunner
                .withPropertyValues(
                        "honeycomb.storage.routing.enabled=true",
                        "honeycomb.storage.per-cell.SampleModel=redis"
                )
                .withBean(ReactiveStringRedisTemplate.class, () -> mock(ReactiveStringRedisTemplate.class))
                .run(context -> {
                    CellDataStore store = context.getBean(CellDataStore.class);
                    assertThat(store).isInstanceOf(CellDataStoreRouter.class);
                });
    }
}
