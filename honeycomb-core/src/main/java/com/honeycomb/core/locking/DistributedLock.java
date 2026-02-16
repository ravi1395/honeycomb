package com.honeycomb.core.locking;

import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Abstraction for distributed locking.
 *
 * <p>Implementations ensure mutual exclusion across multiple Honeycomb instances,
 * used for coordinated idempotency, leader election, and autoscale decisions.</p>
 *
 * @since 1.4.2
 * @see RedisDistributedLock
 */
public interface DistributedLock {

    /**
     * Try to acquire a lock with the given key.
     *
     * @param key   lock key (will be prefixed automatically)
     * @param owner unique owner identifier (e.g. instance ID)
     * @param ttl   lock expiry duration
     * @return {@code Mono<true>} if the lock was acquired, {@code Mono<false>} otherwise
     */
    Mono<Boolean> tryAcquire(String key, String owner, Duration ttl);

    /**
     * Release a lock only if the current owner holds it.
     *
     * @param key   lock key
     * @param owner the owner that acquired the lock
     * @return {@code Mono<true>} if released, {@code Mono<false>} if not held
     */
    Mono<Boolean> release(String key, String owner);

    /**
     * Extend (renew) an existing lock's TTL.
     *
     * @param key   lock key
     * @param owner the owner that holds the lock
     * @param ttl   new TTL
     * @return {@code Mono<true>} if renewed, {@code Mono<false>} if not held
     */
    Mono<Boolean> renew(String key, String owner, Duration ttl);

    /**
     * Check whether a lock is currently held.
     *
     * @param key lock key
     * @return {@code Mono<true>} if the lock exists
     */
    Mono<Boolean> isLocked(String key);

    /**
     * Execute a {@code Mono} action while holding the lock.
     * The lock is acquired before the action and released after completion
     * (or on error).
     *
     * @param key    lock key
     * @param owner  unique owner identifier
     * @param ttl    lock expiry duration
     * @param action the reactive action to execute while holding the lock
     * @param <T>    result type
     * @return the action's result, or an error if the lock could not be acquired
     */
    <T> Mono<T> executeWithLock(String key, String owner, Duration ttl, Mono<T> action);
}
