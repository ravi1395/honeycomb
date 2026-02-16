package com.honeycomb.core.config;

import com.honeycomb.core.events.CellEventPublisher;
import com.honeycomb.core.events.InMemoryCellEventPublisher;
import com.honeycomb.core.events.RedisCellEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * Auto-configures the cell event publisher based on transport setting.
 *
 * <p><b>Added in v1.3</b> — wires the event bus transport layer.</p>
 *
 * <p>Bean selection logic:
 * <ol>
 *   <li>If {@code honeycomb.events.transport=redis} AND a {@code ReactiveStringRedisTemplate}
 *       bean exists → creates {@link RedisCellEventPublisher} (marked {@code @Primary}).</li>
 *   <li>Otherwise, falls back to {@link InMemoryCellEventPublisher} via {@code @ConditionalOnMissingBean}.</li>
 * </ol>
 * </p>
 */
@Configuration
@EnableConfigurationProperties(HoneycombEventProperties.class)
public class CellEventConfig {
    private static final Logger log = LoggerFactory.getLogger(CellEventConfig.class);

    /**
     * Redis-backed event publisher — activated when transport=redis and Redis is available.
     * Marked @Primary so it takes precedence over the in-memory fallback.
     */
    @Bean
    @ConditionalOnProperty(name = "honeycomb.events.transport", havingValue = "redis")
    @ConditionalOnBean(ReactiveStringRedisTemplate.class)
    @Primary
    public CellEventPublisher redisCellEventPublisher(ReactiveStringRedisTemplate redisTemplate,
                                                       ReactiveRedisConnectionFactory connectionFactory,
                                                       ObjectMapper objectMapper,
                                                       MeterRegistry meterRegistry) {
        log.info("Configuring Redis-backed cell event publisher");
        return new RedisCellEventPublisher(redisTemplate, connectionFactory, objectMapper, meterRegistry);
    }

    /**
     * In-memory event publisher — default fallback when Redis transport is not configured
     * or Redis is not available. Uses Reactor Sinks for in-process event delivery.
     */
    @Bean
    @ConditionalOnMissingBean(CellEventPublisher.class)
    public CellEventPublisher inMemoryCellEventPublisher(MeterRegistry meterRegistry) {
        log.info("Configuring in-memory cell event publisher");
        return new InMemoryCellEventPublisher(meterRegistry);
    }
}
