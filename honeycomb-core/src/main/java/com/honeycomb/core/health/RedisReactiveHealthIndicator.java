package com.honeycomb.core.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Reactive health indicator that pings Redis and reports connectivity status.
 *
 * <p>Only registered when a {@link ReactiveRedisConnectionFactory} bean is
 * available (i.e. the application is configured for Redis storage or
 * distributed caching).</p>
 *
 * <p>Reports {@code UP} with the Redis server info when a {@code PING}
 * succeeds within the timeout, or {@code DOWN} with the error detail
 * on failure.</p>
 *
 * @since 1.4.3
 */
@Component("redisHealthIndicator")
@ConditionalOnBean(ReactiveRedisConnectionFactory.class)
public class RedisReactiveHealthIndicator implements ReactiveHealthIndicator {
    private static final Logger log = LoggerFactory.getLogger(RedisReactiveHealthIndicator.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final ReactiveRedisConnectionFactory connectionFactory;

    public RedisReactiveHealthIndicator(ReactiveRedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Mono<Health> health() {
        return connectionFactory.getReactiveConnection()
                .ping()
                .timeout(TIMEOUT)
                .map(pong -> Health.up()
                        .withDetail("redis", "connected")
                        .withDetail("ping", pong)
                        .build())
                .onErrorResume(ex -> {
                    log.warn("Redis health check failed: {}", ex.getMessage());
                    return Mono.just(Health.down()
                            .withDetail("redis", "unreachable")
                            .withException(ex)
                            .build());
                });
    }
}
