package com.honeycomb.core.config;

import com.honeycomb.core.service.IdempotencyStore;
import com.honeycomb.core.service.InMemoryIdempotencyStore;
import com.honeycomb.core.service.RedisIdempotencyStore;
import com.honeycomb.core.util.HoneycombConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@Configuration
public class IdempotencyConfig {
    @Bean
    public IdempotencyStore inMemoryIdempotencyStore() {
        return new InMemoryIdempotencyStore();
    }

    @Bean
    @ConditionalOnExpression(HoneycombConstants.ConfigExpressions.IDEMPOTENCY_REDIS)
    @ConditionalOnClass(ReactiveStringRedisTemplate.class)
    @ConditionalOnBean(ReactiveStringRedisTemplate.class)
    public IdempotencyStore redisIdempotencyStore(ReactiveStringRedisTemplate redisTemplate,
                                                  ObjectMapper objectMapper,
                                                  HoneycombIdempotencyProperties properties) {
        return new RedisIdempotencyStore(redisTemplate, objectMapper, properties.getKeyPrefix());
    }
}
