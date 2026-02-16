package com.honeycomb.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for distributed shared-method cache synchronization.
 *
 * <p><b>Added in v1.3</b> — configurable via {@code honeycomb.shared.cache.*} properties.</p>
 *
 * <p>These properties augment the existing cache settings (warmup, refresh interval, etc.)
 * that are already wired via {@code @Value} in {@code SharedwallMethodCache}.
 * When {@code type=redis}, the {@link RedisSharedMethodCacheSync} bean is activated
 * to synchronize metadata across instances.</p>
 */
@ConfigurationProperties(prefix = "honeycomb.shared.cache")
public class HoneycombCacheProperties {

    /** Cache backend: local | redis. Default: local */
    private String type = "local";

    /** Redis key prefix for cache metadata */
    private String redisKeyPrefix = "honeycomb:shared-cache";

    /** Redis pub/sub channel for cache invalidation broadcast */
    private String redisInvalidateChannel = "honeycomb:cache:invalidate";

    /** TTL in seconds for cached metadata in Redis. 0 = no expiry */
    private long redisTtlSeconds = 120;

    /** Whether cache sync is enabled (requires type=redis) */
    private boolean syncEnabled = true;

    // Existing properties (already wired via @Value in SharedwallMethodCache)
    // are preserved — these new properties augment the existing ones

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getRedisKeyPrefix() { return redisKeyPrefix; }
    public void setRedisKeyPrefix(String redisKeyPrefix) { this.redisKeyPrefix = redisKeyPrefix; }

    public String getRedisInvalidateChannel() { return redisInvalidateChannel; }
    public void setRedisInvalidateChannel(String redisInvalidateChannel) { this.redisInvalidateChannel = redisInvalidateChannel; }

    public long getRedisTtlSeconds() { return redisTtlSeconds; }
    public void setRedisTtlSeconds(long redisTtlSeconds) { this.redisTtlSeconds = redisTtlSeconds; }

    public boolean isSyncEnabled() { return syncEnabled; }
    public void setSyncEnabled(boolean syncEnabled) { this.syncEnabled = syncEnabled; }
}
