package com.honeycomb.core.service;

import com.honeycomb.core.annotations.Cell;
import com.honeycomb.core.config.HoneycombVersioningProperties;
import com.honeycomb.core.versioning.CellVersion;
import com.honeycomb.core.versioning.VersionedCell;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages cell version registration and weighted traffic-split dispatch
 * (blue-green / canary deployments).
 *
 * <p>At startup, this service discovers all beans annotated with both
 * {@code @Cell} and {@code @CellVersion} and registers them in a
 * per-cell version map. At dispatch time, a version is selected either
 * from an explicit header or probabilistically from the configured
 * traffic-split weights.</p>
 *
 * <h3>Configuration</h3>
 * <pre>{@code
 * honeycomb:
 *   versioning:
 *     enabled: true
 *     traffic-split:
 *       catalog:
 *         v1: 90
 *         v2: 10
 * }</pre>
 *
 * @since 1.4.2
 * @see CellVersion
 * @see HoneycombVersioningProperties
 */
@Service
public class CellVersioningService {

    private static final Logger log = LoggerFactory.getLogger(CellVersioningService.class);

    private final ApplicationContext context;
    private final HoneycombVersioningProperties props;
    private final MeterRegistry meterRegistry;

    /** cellName → (version → VersionedCell) */
    private final Map<String, Map<String, VersionedCell>> versionMap = new ConcurrentHashMap<>();

    /** Counts how many times each version was selected. */
    private final Map<String, Counter> selectionCounters = new ConcurrentHashMap<>();

    public CellVersioningService(ApplicationContext context,
                                 HoneycombVersioningProperties props,
                                 MeterRegistry meterRegistry) {
        this.context = context;
        this.props = props;
        this.meterRegistry = meterRegistry;
    }

    // ----- lifecycle ---------------------------------------------------------

    @PostConstruct
    public void init() {
        if (!props.isEnabled()) {
            log.info("Cell versioning is disabled");
            return;
        }
        Map<String, Object> cellBeans = context.getBeansWithAnnotation(Cell.class);
        for (Object bean : cellBeans.values()) {
            Class<?> cls = AopUtils.getTargetClass(bean);
            Cell cellAnn = AnnotationUtils.findAnnotation(cls, Cell.class);
            CellVersion verAnn = AnnotationUtils.findAnnotation(cls, CellVersion.class);
            if (cellAnn == null) continue;

            String cellName = resolveCellName(cls, cellAnn);
            String version = verAnn != null ? verAnn.value() : props.getDefaultVersion();

            VersionedCell vc = new VersionedCell(cellName, version, cls, bean);
            versionMap
                    .computeIfAbsent(cellName, k -> new ConcurrentHashMap<>())
                    .put(version, vc);

            log.info("Registered versioned cell: {} @ {} -> {}",
                    cellName, version, cls.getSimpleName());
        }
        log.info("Cell versioning enabled — {} cells with {} total versions",
                versionMap.size(),
                versionMap.values().stream().mapToInt(Map::size).sum());
    }

    // ----- public API --------------------------------------------------------

    /**
     * Whether versioning is enabled and the given cell has multiple versions.
     */
    public boolean isVersioned(String cellName) {
        if (!props.isEnabled()) return false;
        Map<String, VersionedCell> versions = versionMap.get(cellName);
        return versions != null && versions.size() > 1;
    }

    /**
     * Returns the set of registered versions for a cell.
     */
    public Set<String> getVersions(String cellName) {
        Map<String, VersionedCell> versions = versionMap.get(cellName);
        return versions != null ? versions.keySet() : Set.of();
    }

    /**
     * Returns the bean for a specific version of a cell, or {@code null}.
     */
    public Object getBeanForVersion(String cellName, String version) {
        Map<String, VersionedCell> versions = versionMap.get(cellName);
        if (versions == null) return null;
        VersionedCell vc = versions.get(version);
        return vc != null ? vc.bean() : null;
    }

    /**
     * Selects a cell version based on the traffic-split weights.
     *
     * <p>If an explicit version header is provided and matches a registered
     * version, it wins unconditionally. Otherwise, a weighted random
     * selection is performed.</p>
     *
     * @param cellName      the logical cell name
     * @param explicitVersion version from the request header, may be {@code null}
     * @return the selected version label, or the default version if none matches
     */
    public String selectVersion(String cellName, String explicitVersion) {
        Map<String, VersionedCell> versions = versionMap.get(cellName);
        if (versions == null || versions.isEmpty()) {
            return props.getDefaultVersion();
        }

        // Explicit version request
        if (explicitVersion != null && !explicitVersion.isBlank()) {
            if (versions.containsKey(explicitVersion)) {
                recordSelection(cellName, explicitVersion);
                return explicitVersion;
            }
            log.warn("Requested version '{}' not found for cell '{}'; falling back to traffic-split",
                    explicitVersion, cellName);
        }

        // Traffic-split weighted selection
        Map<String, Integer> weights = props.resolveTrafficSplit(cellName);
        if (weights.isEmpty()) {
            // No split configured — use default version or first available
            String fallback = versions.containsKey(props.getDefaultVersion())
                    ? props.getDefaultVersion()
                    : versions.keySet().iterator().next();
            recordSelection(cellName, fallback);
            return fallback;
        }

        // Weighted random pick among registered versions
        int total = 0;
        List<Map.Entry<String, Integer>> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> e : weights.entrySet()) {
            if (versions.containsKey(e.getKey())) {
                entries.add(e);
                total += Math.max(0, e.getValue());
            }
        }
        if (total <= 0 || entries.isEmpty()) {
            String fallback = versions.containsKey(props.getDefaultVersion())
                    ? props.getDefaultVersion()
                    : versions.keySet().iterator().next();
            recordSelection(cellName, fallback);
            return fallback;
        }

        int r = ThreadLocalRandom.current().nextInt(total);
        int cum = 0;
        for (Map.Entry<String, Integer> e : entries) {
            cum += Math.max(0, e.getValue());
            if (r < cum) {
                recordSelection(cellName, e.getKey());
                return e.getKey();
            }
        }

        // Should not reach here, but safety fallback
        String selected = entries.get(entries.size() - 1).getKey();
        recordSelection(cellName, selected);
        return selected;
    }

    /**
     * Updates the traffic-split weights for a cell at runtime.
     *
     * @param cellName the cell name
     * @param newWeights version → weight map
     */
    public void updateTrafficSplit(String cellName, Map<String, Integer> newWeights) {
        props.getTrafficSplit().put(cellName, new HashMap<>(newWeights));
        log.info("Updated traffic split for cell '{}': {}", cellName, newWeights);
    }

    /**
     * Returns summary information about all versioned cells and their traffic splits.
     */
    public List<VersionedCell.TrafficSplitInfo> listTrafficSplits() {
        List<VersionedCell.TrafficSplitInfo> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, VersionedCell>> entry : versionMap.entrySet()) {
            String cellName = entry.getKey();
            Map<String, Integer> weights = props.resolveTrafficSplit(cellName);
            String active = findActiveVersion(cellName, weights);
            result.add(new VersionedCell.TrafficSplitInfo(cellName, weights, active));
        }
        return result;
    }

    /**
     * Returns version info list for a specific cell.
     */
    public List<VersionedCell.VersionInfo> getCellVersionInfo(String cellName) {
        Map<String, VersionedCell> versions = versionMap.get(cellName);
        if (versions == null) return List.of();
        Map<String, Integer> weights = props.resolveTrafficSplit(cellName);
        List<VersionedCell.VersionInfo> result = new ArrayList<>();
        for (VersionedCell vc : versions.values()) {
            int weight = weights.getOrDefault(vc.version(), 0);
            result.add(new VersionedCell.VersionInfo(
                    cellName, vc.version(), vc.beanClass().getName(),
                    weight, weight > 0));
        }
        return result;
    }

    // ----- private helpers ---------------------------------------------------

    private String resolveCellName(Class<?> cls, Cell ann) {
        String name = ann.value();
        if (name == null || name.isBlank()) {
            name = cls.getSimpleName();
            if (name.endsWith("Cell")) {
                name = name.substring(0, name.length() - 4);
            }
            name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }
        return name;
    }

    private void recordSelection(String cellName, String version) {
        String key = cellName + ":" + version;
        selectionCounters
                .computeIfAbsent(key, k -> meterRegistry.counter(
                        "honeycomb.versioning.selections",
                        "cell", cellName, "version", version))
                .increment();
    }

    private String findActiveVersion(String cellName, Map<String, Integer> weights) {
        if (weights.isEmpty()) return props.getDefaultVersion();
        return weights.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(props.getDefaultVersion());
    }
}
