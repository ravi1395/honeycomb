package com.example.honeycomb.web;

import com.example.honeycomb.service.RequestMetricsService;
import com.example.honeycomb.web.SharedwallMethodCache;
import com.example.honeycomb.util.HoneycombConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping(HoneycombConstants.Paths.HONEYCOMB_METRICS)
@Tag(name = HoneycombConstants.Docs.TAG_METRICS, description = HoneycombConstants.Docs.TAG_METRICS_DESC)
public class MetricsController {
    private final RequestMetricsService metricsService;
    private final SharedwallMethodCache sharedwallMethodCache;

    public MetricsController(RequestMetricsService metricsService, SharedwallMethodCache sharedwallMethodCache) {
        this.metricsService = metricsService;
        this.sharedwallMethodCache = sharedwallMethodCache;
    }

    @Operation(summary = HoneycombConstants.Docs.METRICS_CELL_COUNTS)
    @GetMapping(HoneycombConstants.Names.SEPARATOR_SLASH + HoneycombConstants.Paths.CELLS)
    public Map<String, Long> cellCounts() {
        return metricsService.snapshotCounts();
    }

    @GetMapping(HoneycombConstants.Names.SEPARATOR_SLASH + "shared-cache")
    public Map<String, Object> sharedCacheStats() {
        return Map.of(
            "methodCount", sharedwallMethodCache.getMethodCount(),
            "buildDurationMs", sharedwallMethodCache.getBuildDurationMs()
        );
    }
}
