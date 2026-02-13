package com.honeycomb.core.web;

import com.honeycomb.core.service.RequestMetricsService;
import com.honeycomb.core.util.HoneycombConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping(HoneycombConstants.Paths.HONEYCOMB_METRICS)
@Tag(name = HoneycombConstants.Docs.TAG_METRICS, description = HoneycombConstants.Docs.TAG_METRICS_DESC)
public class MetricsController {
    private final RequestMetricsService metricsService;
    private final com.honeycomb.core.service.SharedwallMethodCache sharedwallMethodCache;

    public MetricsController(RequestMetricsService metricsService, com.honeycomb.core.service.SharedwallMethodCache sharedwallMethodCache) {
        this.metricsService = metricsService;
        this.sharedwallMethodCache = sharedwallMethodCache;
    }

    @Operation(summary = HoneycombConstants.Docs.METRICS_CELL_COUNTS)
    @GetMapping(HoneycombConstants.Names.SEPARATOR_SLASH + HoneycombConstants.Paths.CELLS)
    public Map<String, Long> cellCounts() {
        return metricsService.snapshotCounts();
    }

    @Operation(summary = HoneycombConstants.Docs.METRICS_SHARED_CACHE_STATS)
    @GetMapping(HoneycombConstants.Names.SEPARATOR_SLASH + "shared-cache")
    public Map<String, Object> sharedCacheStats() {
        return Map.of(
            "methodCount", sharedwallMethodCache.getMethodCount(),
            "buildDurationMs", sharedwallMethodCache.getBuildDurationMs(),
            "lastRefreshDurationMs", sharedwallMethodCache.getLastRefreshDurationMs(),
            "lastRefreshAtMs", sharedwallMethodCache.getLastRefreshAtMs(),
            "lastRefreshAgeMs", sharedwallMethodCache.getLastRefreshAgeMs(),
            "nextAllowedRefreshAtMs", sharedwallMethodCache.getNextAllowedRefreshAtMs(),
            "consecutiveFailures", sharedwallMethodCache.getConsecutiveFailures()
        );
    }

    @Operation(summary = HoneycombConstants.Docs.METRICS_SHARED_CACHE_REFRESH)
    @PostMapping(HoneycombConstants.Names.SEPARATOR_SLASH + "shared-cache" + HoneycombConstants.Names.SEPARATOR_SLASH + "refresh")
    public Map<String, Object> refreshSharedCache() {
        long durationMs = sharedwallMethodCache.rebuild();
        return Map.of(
            HoneycombConstants.JsonKeys.STATUS, HoneycombConstants.Status.OK,
            "buildDurationMs", durationMs
        );
    }

    @Operation(summary = HoneycombConstants.Docs.METRICS_SHARED_CACHE_INVALIDATE_ALL)
    @DeleteMapping(HoneycombConstants.Names.SEPARATOR_SLASH + "shared-cache")
    public Map<String, Object> invalidateSharedCache() {
        sharedwallMethodCache.invalidateAll();
        return Map.of(
            HoneycombConstants.JsonKeys.STATUS, HoneycombConstants.Status.OK
        );
    }

    @Operation(summary = HoneycombConstants.Docs.METRICS_SHARED_CACHE_INVALIDATE_ONE)
    @DeleteMapping(HoneycombConstants.Names.SEPARATOR_SLASH + "shared-cache" + HoneycombConstants.Names.SEPARATOR_SLASH + "{method}")
    public ResponseEntity<Map<String, Object>> invalidateSharedCacheMethod(@PathVariable("method") String method) {
        boolean removed = sharedwallMethodCache.invalidateMethod(method);
        if (!removed) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
            HoneycombConstants.JsonKeys.STATUS, HoneycombConstants.Status.OK,
            HoneycombConstants.JsonKeys.METHOD, method
        ));
    }
}
