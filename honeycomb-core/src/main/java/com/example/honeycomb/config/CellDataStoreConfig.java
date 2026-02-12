package com.example.honeycomb.config;

import com.example.honeycomb.service.CellDataStore;
import com.example.honeycomb.service.CellDataStoreRouter;
import com.example.honeycomb.service.HibernateReactiveCellDataStore;
import com.example.honeycomb.service.InMemoryCellDataStore;
import com.example.honeycomb.service.RedisCellDataStore;
import com.example.honeycomb.util.HoneycombConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.hibernate.reactive.mutiny.Mutiny;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(HoneycombStorageProperties.class)
public class CellDataStoreConfig {
    @Bean
    @ConditionalOnExpression(HoneycombConstants.ConfigExpressions.STORAGE_REDIS_OR_ROUTING)
    @ConditionalOnBean(ReactiveStringRedisTemplate.class)
    @ConditionalOnClass(ReactiveStringRedisTemplate.class)
    public RedisCellDataStore redisCellDataStore(ReactiveStringRedisTemplate redisTemplate,
                                                 ObjectMapper objectMapper,
                                                 HoneycombStorageProperties storageProperties) {
        return new RedisCellDataStore(redisTemplate, objectMapper, storageProperties);
    }

    @Bean
        @ConditionalOnExpression(HoneycombConstants.ConfigExpressions.STORAGE_HIBERNATE_OR_ROUTING)
            @ConditionalOnProperty(name = HoneycombConstants.ConfigKeys.STORAGE_HIBERNATE_ANNOTATION_FREE,
                havingValue = HoneycombConstants.Values.TRUE, matchIfMissing = true)
    @ConditionalOnBean(Mutiny.SessionFactory.class)
    @ConditionalOnClass(Mutiny.SessionFactory.class)
    public HibernateReactiveCellDataStore hibernateReactiveCellDataStore(Mutiny.SessionFactory sessionFactory,
                                                                        ObjectMapper objectMapper) {
        return new HibernateReactiveCellDataStore(sessionFactory, objectMapper);
    }

    @Bean
    @ConditionalOnExpression(HoneycombConstants.ConfigExpressions.STORAGE_MEMORY_OR_ROUTING)
    public InMemoryCellDataStore inMemoryCellDataStore() {
        return new InMemoryCellDataStore();
    }

    @Bean
    @Primary
        @ConditionalOnProperty(name = HoneycombConstants.ConfigKeys.STORAGE_ROUTING_ENABLED,
            havingValue = HoneycombConstants.Values.TRUE)
    public CellDataStore routingCellDataStore(HoneycombStorageProperties storageProperties,
                                              InMemoryCellDataStore inMemoryCellDataStore,
                                              org.springframework.beans.factory.ObjectProvider<RedisCellDataStore> redisProvider,
                                              org.springframework.beans.factory.ObjectProvider<HibernateReactiveCellDataStore> hibernateProvider) {
        Map<String, CellDataStore> stores = new HashMap<>();
        stores.put(HoneycombConstants.Names.STORE_MEMORY, inMemoryCellDataStore);
        RedisCellDataStore redis = redisProvider.getIfAvailable();
        if (redis != null) {
            stores.put(HoneycombConstants.Names.STORE_REDIS, redis);
        }
        HibernateReactiveCellDataStore hibernate = hibernateProvider.getIfAvailable();
        if (hibernate != null) {
            stores.put(HoneycombConstants.Names.STORE_HIBERNATE, hibernate);
        }
        return new CellDataStoreRouter(storageProperties, stores);
    }
}
