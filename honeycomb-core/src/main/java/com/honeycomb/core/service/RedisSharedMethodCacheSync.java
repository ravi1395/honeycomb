package com.honeycomb.core.service;

import com.honeycomb.core.config.HoneycombCacheProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;

/**
 * Synchronizes shared-method cache metadata across instances via Redis.
 *
 * <p><b>Added in v1.3</b> — enables distributed cache coherence for multi-instance deployments.</p>
 *
 * <p>After each local cache refresh, publishes a compact metadata snapshot
 * (method names + versions) to a Redis hash. Listens on a pub/sub channel
 * for invalidation signals from other instances.</p>
 *
 * <p>Invalidation protocol: messages on the invalidation channel use the format
 * {@code <instanceId>:<methodName|*>}. The sender's instanceId is included so
 * each instance can ignore its own broadcasts (dedup).</p>
 *
 * <p>When Redis is unavailable, gracefully degrades to local-only caching —
 * no exceptions are propagated to callers.</p>
 */
@SuppressWarnings("null")
public class RedisSharedMethodCacheSync {
    private static final Logger log = LoggerFactory.getLogger(RedisSharedMethodCacheSync.class);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ReactiveRedisConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;
    private final SharedwallMethodCache localCache;
    private final HoneycombCacheProperties cacheProperties;

    // Unique ID for this JVM instance — used to dedup invalidation broadcasts
    private final String instanceId;

    // Micrometer counters for observability
    private final Counter syncSuccessCounter;
    private final Counter syncFailureCounter;
    private final Counter invalidationReceivedCounter;

    // Redis pub/sub subscription handle — disposed on destroy()
    private Disposable subscription;

    public RedisSharedMethodCacheSync(ReactiveStringRedisTemplate redisTemplate,
                                      ReactiveRedisConnectionFactory connectionFactory,
                                      ObjectMapper objectMapper,
                                      SharedwallMethodCache localCache,
                                      HoneycombCacheProperties cacheProperties,
                                      MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.connectionFactory = connectionFactory;
        this.objectMapper = objectMapper;
        this.localCache = localCache;
        this.cacheProperties = cacheProperties;
        // Short UUID prefix identifies this instance in Redis keys and invalidation messages
        this.instanceId = UUID.randomUUID().toString().substring(0, 8);
        this.syncSuccessCounter = meterRegistry.counter("honeycomb.shared.cache.redis.sync", "result", "success");
        this.syncFailureCounter = meterRegistry.counter("honeycomb.shared.cache.redis.sync", "result", "failure");
        this.invalidationReceivedCounter = meterRegistry.counter("honeycomb.shared.cache.redis.invalidation.received");
    }

    @PostConstruct
    public void init() {
        if (!cacheProperties.isSyncEnabled()) {
            log.info("Redis cache sync disabled");
            return;
        }

        // Subscribe to invalidation channel
        try {
            ReactiveRedisMessageListenerContainer container =
                    new ReactiveRedisMessageListenerContainer(connectionFactory);
            subscription = container.receive(ChannelTopic.of(cacheProperties.getRedisInvalidateChannel()))
                    .publishOn(Schedulers.boundedElastic())
                    .subscribe(message -> handleInvalidation(message.getMessage()));
            log.info("Subscribed to Redis cache invalidation channel: {}", cacheProperties.getRedisInvalidateChannel());
        } catch (Exception ex) {
            log.warn("Failed to subscribe to Redis invalidation channel — degrading to local-only: {}", ex.getMessage());
        }
    }

    /**
     * After a local cache refresh, publish metadata to Redis so other instances
     * can see the current method inventory.
     *
     * <p>Data is stored under key: {@code <prefix>:methods:<instanceId>}
     * as a JSON map of method name → list of available versions.</p>
     */
    public Mono<Void> publishCacheMetadata() {
        // Short-circuit if distributed sync is not configured/enabled
        if (!"redis".equalsIgnoreCase(cacheProperties.getType()) || !cacheProperties.isSyncEnabled()) {
            return Mono.empty();
        }

        Map<String, List<SharedwallMethodCache.MethodCandidate>> allCandidates = localCache.getAllCandidates();
        // Build a compact representation: methodName -> [versions] for Redis storage
        Map<String, List<String>> metadata = new LinkedHashMap<>();
        allCandidates.forEach((name, candidates) -> {
            List<String> versions = candidates.stream()
                    .map(c -> c.getSharedwall() != null && c.getSharedwall().version() != null
                            ? c.getSharedwall().version() : "v1")
                    .distinct()
                    .toList();
            metadata.put(name, versions);
        });

        String key = cacheProperties.getRedisKeyPrefix() + ":methods:" + instanceId;
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(metadata))
                .flatMap(json -> {
                    Duration ttl = cacheProperties.getRedisTtlSeconds() > 0
                            ? Duration.ofSeconds(cacheProperties.getRedisTtlSeconds())
                            : null;
                    Mono<Boolean> set = redisTemplate.opsForValue().set(key, json);
                    if (ttl != null) {
                        set = redisTemplate.opsForValue().set(key, json, ttl);
                    }
                    return set;
                })
                .doOnSuccess(v -> {
                    syncSuccessCounter.increment();
                    log.debug("Published cache metadata to Redis: key={}, methods={}", key, metadata.size());
                })
                .doOnError(ex -> {
                    syncFailureCounter.increment();
                    log.warn("Failed to publish cache metadata to Redis: {}", ex.getMessage());
                })
                .then();
    }

    /**
     * Broadcast a cache invalidation signal to all instances.
     *
     * @param methodName the method to invalidate, or "*" for full invalidation
     */
    public Mono<Void> broadcastInvalidation(String methodName) {
        if (!"redis".equalsIgnoreCase(cacheProperties.getType()) || !cacheProperties.isSyncEnabled()) {
            return Mono.empty();
        }

        String message = instanceId + ":" + (methodName == null ? "*" : methodName);
        return redisTemplate.convertAndSend(cacheProperties.getRedisInvalidateChannel(), message)
                .doOnSuccess(v -> log.debug("Broadcast cache invalidation: {}", message))
                .doOnError(ex -> log.warn("Failed to broadcast invalidation: {}", ex.getMessage()))
                .then();
    }

    /**
     * Read combined cache metadata from all instances in Redis.
     */
    public Mono<Map<String, List<String>>> readClusterMetadata() {
        String pattern = cacheProperties.getRedisKeyPrefix() + ":methods:*";
        return redisTemplate.keys(pattern)
                .flatMap(key -> redisTemplate.opsForValue().get(key))
                .flatMap(json -> {
                    try {
                        Map<String, List<String>> map = objectMapper.readValue(json,
                                new TypeReference<Map<String, List<String>>>() {});
                        return Mono.just(map);
                    } catch (Exception ex) {
                        return Mono.empty();
                    }
                })
                .reduce(new LinkedHashMap<>(), (combined, entry) -> {
                    entry.forEach((name, versions) ->
                            combined.merge(name, versions, (existing, incoming) -> {
                                Set<String> merged = new LinkedHashSet<>(existing);
                                merged.addAll(incoming);
                                return new ArrayList<>(merged);
                            }));
                    return combined;
                });
    }

    /**
     * Handles incoming invalidation messages from Redis pub/sub.
     * Message format: {@code <senderId>:<target>} where target is a method name or "*".
     * Own broadcasts are ignored (dedup via instanceId comparison).
     */
    private void handleInvalidation(String message) {
        invalidationReceivedCounter.increment();
        if (message == null) return;

        // Parse the sender ID and invalidation target from the message
        String[] parts = message.split(":", 2);
        String senderId = parts[0];
        String target = parts.length > 1 ? parts[1] : "*";

        // Ignore our own broadcasts to avoid redundant cache invalidation
        if (instanceId.equals(senderId)) return;

        log.info("Received cache invalidation from instance={}, target={}", senderId, target);
        if ("*".equals(target)) {
            // Full cache invalidation: clear everything and rebuild from scratch
            localCache.invalidateAll();
            localCache.rebuild();
        } else {
            // Targeted invalidation: only clear the specified method
            localCache.invalidateMethod(target);
        }
    }

    public void destroy() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }
}
