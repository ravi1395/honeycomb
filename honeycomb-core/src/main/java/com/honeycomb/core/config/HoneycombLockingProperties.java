package com.honeycomb.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for distributed locking, bound to {@code honeycomb.locking.*}.
 *
 * <h3>Example configuration</h3>
 * <pre>{@code
 * honeycomb:
 *   locking:
 *     enabled: true
 *     type: redis          # redis (default)
 *     key-prefix: "honeycomb:lock:"
 *     default-ttl: 30s
 *     retry-delay: 100ms
 *     max-retries: 3
 *     leader-election:
 *       enabled: true
 *       key: "honeycomb:leader"
 *       ttl: 30s
 *       renewal-interval: 10s
 * }</pre>
 *
 * @since 1.4.2
 * @see com.honeycomb.core.locking.DistributedLock
 * @see com.honeycomb.core.locking.LeaderElectionService
 */
@ConfigurationProperties(prefix = "honeycomb.locking")
public class HoneycombLockingProperties {

    /** Whether distributed locking is enabled. */
    private boolean enabled = false;

    /** Lock backend type. Currently only {@code redis} is supported. */
    private String type = "redis";

    /** Key prefix for lock keys in Redis. */
    private String keyPrefix = "honeycomb:lock:";

    /** Default lock TTL — after this duration an unreleased lock expires automatically. */
    private Duration defaultTtl = Duration.ofSeconds(30);

    /** Delay between retry attempts when acquiring a lock. */
    private Duration retryDelay = Duration.ofMillis(100);

    /** Maximum number of retries when acquiring a lock. */
    private int maxRetries = 3;

    /** Leader election sub-configuration. */
    private LeaderElection leaderElection = new LeaderElection();

    // ----- getters / setters -------------------------------------------------

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }

    public Duration getDefaultTtl() { return defaultTtl; }
    public void setDefaultTtl(Duration defaultTtl) { this.defaultTtl = defaultTtl; }

    public Duration getRetryDelay() { return retryDelay; }
    public void setRetryDelay(Duration retryDelay) { this.retryDelay = retryDelay; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public LeaderElection getLeaderElection() { return leaderElection; }
    public void setLeaderElection(LeaderElection leaderElection) { this.leaderElection = leaderElection; }

    // ----- inner class -------------------------------------------------------

    public static class LeaderElection {
        /** Whether leader election is enabled. */
        private boolean enabled = false;

        /** Redis key used for the leader lock. */
        private String key = "honeycomb:leader";

        /** Time-to-live for the leader lock. */
        private Duration ttl = Duration.ofSeconds(30);

        /** How often the current leader renews its lock. */
        private Duration renewalInterval = Duration.ofSeconds(10);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }

        public Duration getTtl() { return ttl; }
        public void setTtl(Duration ttl) { this.ttl = ttl; }

        public Duration getRenewalInterval() { return renewalInterval; }
        public void setRenewalInterval(Duration renewalInterval) { this.renewalInterval = renewalInterval; }
    }
}
