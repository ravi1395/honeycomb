package com.honeycomb.core.config;

import com.honeycomb.core.service.RedisSharedMethodCacheSync;
import com.honeycomb.core.service.SharedwallMethodCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * Configures distributed cache synchronization when Redis is available.
 *
 * <p><b>Added in v1.3</b> — enables cross-instance shared-method cache sync.</p>
 *
 * <p>Activation conditions:
 * <ol>
 *   <li>{@code honeycomb.shared.cache.type=redis} must be set in config</li>
 *   <li>A {@code ReactiveStringRedisTemplate} bean must exist in the context
 *       (typically auto-configured by Spring Boot when {@code spring-boot-starter-data-redis-reactive}
 *       is on the classpath)</li>
 * </ol>
 * When both conditions are met, creates a {@link RedisSharedMethodCacheSync} bean
 * that publishes cache metadata and listens for invalidation signals.</p>
 */
@Configuration
@EnableConfigurationProperties(HoneycombCacheProperties.class)
public class DistributedCacheConfig {
    private static final Logger log = LoggerFactory.getLogger(DistributedCacheConfig.class);

    @Bean
    @ConditionalOnProperty(name = "honeycomb.shared.cache.type", havingValue = "redis")
    @ConditionalOnBean(ReactiveStringRedisTemplate.class)
    public RedisSharedMethodCacheSync redisSharedMethodCacheSync(
            ReactiveStringRedisTemplate redisTemplate,
            ReactiveRedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper,
            SharedwallMethodCache localCache,
            HoneycombCacheProperties cacheProperties,
            MeterRegistry meterRegistry) {
        log.info("Enabling distributed Redis cache sync for shared methods");
        return new RedisSharedMethodCacheSync(
                redisTemplate, connectionFactory, objectMapper,
                localCache, cacheProperties, meterRegistry);
    }
}
