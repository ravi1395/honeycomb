package com.honeycomb.core.web;

import com.honeycomb.core.service.RedisSharedMethodCacheSync;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Admin endpoints for the distributed shared-method cache.
 * Only activated when {@link RedisSharedMethodCacheSync} is available.
 *
 * <p><b>Added in v1.3</b> — operational endpoints for cache management in multi-instance deployments.</p>
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /honeycomb/admin/cache/cluster} — view cluster-wide metadata</li>
 *   <li>{@code POST /honeycomb/admin/cache/invalidate} — broadcast invalidation</li>
 *   <li>{@code POST /honeycomb/admin/cache/sync} — force-sync local metadata to Redis</li>
 * </ul>
 * Protected by the standard Honeycomb API key filter.</p>
 */
@RestController
@RequestMapping("/honeycomb/admin/cache")
@Tag(name = "Distributed Cache Admin", description = "Distributed shared-method cache management")
@ConditionalOnBean(RedisSharedMethodCacheSync.class)
public class DistributedCacheController {

    private final RedisSharedMethodCacheSync cacheSync;

    public DistributedCacheController(RedisSharedMethodCacheSync cacheSync) {
        this.cacheSync = cacheSync;
    }

    @Operation(summary = "View cluster-wide shared method cache metadata")
    @GetMapping("/cluster")
    public Mono<Map<String, List<String>>> clusterMetadata() {
        return cacheSync.readClusterMetadata();
    }

    @Operation(summary = "Broadcast cache invalidation to all instances")
    @PostMapping("/invalidate")
    public Mono<Map<String, String>> broadcastInvalidation(
            @RequestParam(name = "method", required = false) String method) {
        return cacheSync.broadcastInvalidation(method)
                .thenReturn(Map.of(
                        "status", "invalidation-broadcast",
                        "target", method == null ? "*" : method
                ));
    }

    @Operation(summary = "Force sync local cache metadata to Redis")
    @PostMapping("/sync")
    public Mono<Map<String, String>> forceSync() {
        return cacheSync.publishCacheMetadata()
                .thenReturn(Map.of("status", "synced"));
    }
}
