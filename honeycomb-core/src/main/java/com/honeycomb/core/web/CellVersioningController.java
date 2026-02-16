package com.honeycomb.core.web;

import com.honeycomb.core.config.HoneycombVersioningProperties;
import com.honeycomb.core.service.CellVersioningService;
import com.honeycomb.core.versioning.VersionedCell;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * REST controller for managing cell versioning and blue-green / canary traffic splits.
 *
 * <p>Active only when {@code honeycomb.versioning.enabled=true}.</p>
 *
 * @since 1.4.2
 */
@RestController
@RequestMapping("/honeycomb/versioning")
@ConditionalOnProperty(name = "honeycomb.versioning.enabled", havingValue = "true")
@Tag(name = "Cell Versioning", description = "Blue-green and canary deployment controls for versioned cells")
public class CellVersioningController {

    private final CellVersioningService versioningService;
    private final HoneycombVersioningProperties versioningProperties;

    public CellVersioningController(CellVersioningService versioningService,
                                    HoneycombVersioningProperties versioningProperties) {
        this.versioningService = versioningService;
        this.versioningProperties = versioningProperties;
    }

    @GetMapping("/splits")
    @Operation(summary = "List all cell traffic splits",
               description = "Returns versioned cells with their configured traffic-split weights")
    public Mono<ResponseEntity<List<VersionedCell.TrafficSplitInfo>>> listTrafficSplits() {
        return Mono.just(ResponseEntity.ok(versioningService.listTrafficSplits()));
    }

    @GetMapping("/splits/{cellName}")
    @Operation(summary = "Get traffic split for a specific cell")
    public Mono<ResponseEntity<List<VersionedCell.VersionInfo>>> getCellVersions(
            @PathVariable String cellName) {
        List<VersionedCell.VersionInfo> info = versioningService.getCellVersionInfo(cellName);
        if (info.isEmpty()) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        return Mono.just(ResponseEntity.ok(info));
    }

    @PutMapping("/splits/{cellName}")
    @Operation(summary = "Update traffic split for a cell",
               description = "Accepts a map of version → weight (e.g. {\"v1\": 90, \"v2\": 10})")
    public Mono<ResponseEntity<Map<String, Object>>> updateTrafficSplit(
            @PathVariable String cellName,
            @RequestBody Map<String, Integer> weights) {
        versioningService.updateTrafficSplit(cellName, weights);
        return Mono.just(ResponseEntity.ok(Map.of(
                "cell", cellName,
                "trafficSplit", weights,
                "status", "updated"
        )));
    }

    @GetMapping("/cells/{cellName}/versions")
    @Operation(summary = "List registered versions for a cell")
    public Mono<ResponseEntity<Map<String, Object>>> getVersions(@PathVariable String cellName) {
        var versions = versioningService.getVersions(cellName);
        if (versions.isEmpty()) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        return Mono.just(ResponseEntity.ok(Map.of(
                "cell", cellName,
                "versions", versions,
                "versioned", versioningService.isVersioned(cellName)
        )));
    }

    @PostMapping("/cells/{cellName}/promote/{version}")
    @Operation(summary = "Promote a version to 100% traffic",
               description = "Sets the specified version to weight 100 and all others to 0 — blue-green cutover")
    public Mono<ResponseEntity<Map<String, Object>>> promoteVersion(
            @PathVariable String cellName,
            @PathVariable String version) {
        var allVersions = versioningService.getVersions(cellName);
        if (allVersions.isEmpty() || !allVersions.contains(version)) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        Map<String, Integer> newWeights = new java.util.HashMap<>();
        for (String v : allVersions) {
            newWeights.put(v, v.equals(version) ? 100 : 0);
        }
        versioningService.updateTrafficSplit(cellName, newWeights);
        return Mono.just(ResponseEntity.ok(Map.of(
                "cell", cellName,
                "promoted", version,
                "trafficSplit", newWeights,
                "status", "promoted"
        )));
    }
}
