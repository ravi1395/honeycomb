package com.honeycomb.core.locking;

import com.honeycomb.core.config.HoneycombLockingProperties;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Leader election service using a distributed lock.
 *
 * <p>One instance among all Honeycomb replicas becomes the leader by
 * acquiring a well-known lock key. The leader periodically renews the
 * lock; if it fails or shuts down, another instance will acquire
 * leadership after TTL expiry.</p>
 *
 * <p>Typical uses: coordinating autoscale decisions, running scheduled
 * maintenance tasks, cache warm-up, etc.</p>
 *
 * @since 1.4.2
 * @see DistributedLock
 * @see HoneycombLockingProperties.LeaderElection
 */
public class LeaderElectionService {

    private static final Logger log = LoggerFactory.getLogger(LeaderElectionService.class);

    private final DistributedLock lock;
    private final HoneycombLockingProperties properties;
    private final MeterRegistry meterRegistry;

    /** Unique identifier for this instance (owner of the lock). */
    private final String instanceId = UUID.randomUUID().toString();

    private final AtomicBoolean leader = new AtomicBoolean(false);
    private Disposable renewalSubscription;

    public LeaderElectionService(DistributedLock lock,
                                 HoneycombLockingProperties properties,
                                 MeterRegistry meterRegistry) {
        this.lock = lock;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        meterRegistry.gauge("honeycomb.leader.is_leader", leader, b -> b.get() ? 1.0 : 0.0);
    }

    @PostConstruct
    public void start() {
        if (!properties.isEnabled() || !properties.getLeaderElection().isEnabled()) {
            log.info("Leader election is disabled");
            return;
        }
        HoneycombLockingProperties.LeaderElection le = properties.getLeaderElection();
        Duration interval = le.getRenewalInterval();

        log.info("Starting leader election — instanceId={}, key={}, ttl={}, renewal={}",
                instanceId, le.getKey(), le.getTtl(), interval);

        renewalSubscription = Flux.interval(Duration.ZERO, interval)
                .flatMap(tick -> tryBecomeLeader())
                .subscribe();
    }

    @PreDestroy
    public void stop() {
        if (renewalSubscription != null && !renewalSubscription.isDisposed()) {
            renewalSubscription.dispose();
        }
        if (leader.get()) {
            relinquish().block(Duration.ofSeconds(5));
        }
    }

    /**
     * Returns {@code true} if this instance is the current leader.
     */
    public boolean isLeader() {
        return leader.get();
    }

    /**
     * Returns the unique instance ID.
     */
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * Execute an action only if this instance is the leader.
     *
     * @param action the reactive action to run
     * @param <T>    result type
     * @return the result if this instance is leader, or {@code Mono.empty()} otherwise
     */
    public <T> Mono<T> executeIfLeader(Mono<T> action) {
        if (!leader.get()) {
            return Mono.empty();
        }
        return action;
    }

    /**
     * Voluntarily give up leadership.
     */
    public Mono<Boolean> relinquish() {
        HoneycombLockingProperties.LeaderElection le = properties.getLeaderElection();
        return lock.release(le.getKey(), instanceId)
                .doOnNext(released -> {
                    if (Boolean.TRUE.equals(released)) {
                        leader.set(false);
                        log.info("Relinquished leadership — instanceId={}", instanceId);
                    }
                });
    }

    // ----- private -----------------------------------------------------------

    private Mono<Boolean> tryBecomeLeader() {
        HoneycombLockingProperties.LeaderElection le = properties.getLeaderElection();
        if (leader.get()) {
            // Already leader — renew.
            return lock.renew(le.getKey(), instanceId, le.getTtl())
                    .doOnNext(renewed -> {
                        if (Boolean.FALSE.equals(renewed)) {
                            leader.set(false);
                            log.warn("Lost leadership (renewal failed) — instanceId={}", instanceId);
                        }
                    })
                    .onErrorResume(e -> {
                        leader.set(false);
                        log.warn("Lost leadership (error) — instanceId={}: {}", instanceId, e.getMessage());
                        return Mono.just(false);
                    });
        }
        // Not leader — try to acquire.
        return lock.tryAcquire(le.getKey(), instanceId, le.getTtl())
                .doOnNext(acquired -> {
                    if (Boolean.TRUE.equals(acquired)) {
                        leader.set(true);
                        log.info("Became leader — instanceId={}", instanceId);
                    }
                })
                .onErrorResume(e -> {
                    log.debug("Leader election attempt failed: {}", e.getMessage());
                    return Mono.just(false);
                });
    }
}
