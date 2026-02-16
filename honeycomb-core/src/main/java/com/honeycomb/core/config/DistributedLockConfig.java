package com.honeycomb.core.config;

import com.honeycomb.core.locking.DistributedLock;
import com.honeycomb.core.locking.LeaderElectionService;
import com.honeycomb.core.locking.RedisDistributedLock;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * Auto-wires distributed locking beans when {@code honeycomb.locking.enabled=true}.
 *
 * @since 1.4.2
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "honeycomb.locking.enabled", havingValue = "true")
public class DistributedLockConfig {

    @Bean
    public DistributedLock distributedLock(ReactiveStringRedisTemplate redisTemplate,
                                           HoneycombLockingProperties properties,
                                           MeterRegistry meterRegistry) {
        return new RedisDistributedLock(redisTemplate, properties, meterRegistry);
    }

    @Bean
    @ConditionalOnProperty(name = "honeycomb.locking.leader-election.enabled", havingValue = "true")
    public LeaderElectionService leaderElectionService(DistributedLock lock,
                                                       HoneycombLockingProperties properties,
                                                       MeterRegistry meterRegistry) {
        return new LeaderElectionService(lock, properties, meterRegistry);
    }
}
