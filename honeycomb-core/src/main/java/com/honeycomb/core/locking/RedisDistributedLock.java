package com.honeycomb.core.locking;

import com.honeycomb.core.config.HoneycombLockingProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Redis-backed distributed lock using atomic {@code SET NX EX} and Lua-script release.
 *
 * <p>The lock is stored as a simple key whose value is the owner ID and whose
 * expiry is the TTL. Release and renewal are performed via Lua scripts to
 * ensure atomicity.</p>
 *
 * @since 1.4.2
 */
public class RedisDistributedLock implements DistributedLock {

    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLock.class);

    /**
     * Lua: release only if the stored value matches the owner.
     */
    private static final String RELEASE_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end";

    /**
     * Lua: renew TTL only if the stored value matches the owner.
     */
    private static final String RENEW_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('pexpire', KEYS[1], ARGV[2]) " +
            "else " +
            "  return 0 " +
            "end";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final HoneycombLockingProperties properties;
    private final Counter acquireCounter;
    private final Counter releaseCounter;
    private final Counter failCounter;

    public RedisDistributedLock(ReactiveStringRedisTemplate redisTemplate,
                                HoneycombLockingProperties properties,
                                MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.acquireCounter = meterRegistry.counter("honeycomb.lock.acquire");
        this.releaseCounter = meterRegistry.counter("honeycomb.lock.release");
        this.failCounter = meterRegistry.counter("honeycomb.lock.fail");
    }

    @Override
    public Mono<Boolean> tryAcquire(String key, String owner, Duration ttl) {
        String fullKey = properties.getKeyPrefix() + key;
        return redisTemplate.opsForValue()
                .setIfAbsent(fullKey, owner, ttl)
                .defaultIfEmpty(false)
                .doOnNext(acquired -> {
                    if (Boolean.TRUE.equals(acquired)) {
                        acquireCounter.increment();
                        log.debug("Lock acquired: key={}, owner={}, ttl={}", fullKey, owner, ttl);
                    } else {
                        failCounter.increment();
                        log.debug("Lock not acquired (held): key={}, owner={}", fullKey, owner);
                    }
                });
    }

    @Override
    public Mono<Boolean> release(String key, String owner) {
        String fullKey = properties.getKeyPrefix() + key;
        RedisScript<Long> script = RedisScript.of(RELEASE_SCRIPT, Long.class);
        return redisTemplate.execute(script, List.of(fullKey), List.of(owner))
                .next()
                .map(result -> result != null && result > 0)
                .defaultIfEmpty(false)
                .doOnNext(released -> {
                    if (Boolean.TRUE.equals(released)) {
                        releaseCounter.increment();
                        log.debug("Lock released: key={}, owner={}", fullKey, owner);
                    }
                });
    }

    @Override
    public Mono<Boolean> renew(String key, String owner, Duration ttl) {
        String fullKey = properties.getKeyPrefix() + key;
        RedisScript<Long> script = RedisScript.of(RENEW_SCRIPT, Long.class);
        return redisTemplate.execute(script,
                        List.of(fullKey),
                        List.of(owner, String.valueOf(ttl.toMillis())))
                .next()
                .map(result -> result != null && result > 0)
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Boolean> isLocked(String key) {
        String fullKey = properties.getKeyPrefix() + key;
        return redisTemplate.hasKey(fullKey);
    }

    @Override
    public <T> Mono<T> executeWithLock(String key, String owner, Duration ttl, Mono<T> action) {
        return tryAcquire(key, owner, ttl)
                .flatMap(acquired -> {
                    if (!acquired) {
                        return Mono.error(new LockNotAcquiredException(
                                "Failed to acquire lock: " + key));
                    }
                    return action
                            .doFinally(signal -> release(key, owner).subscribe());
                });
    }
}
