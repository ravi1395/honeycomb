package com.honeycomb.core.versioning;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of a versioned cell registration.
 *
 * @param cellName   logical cell name (shared across versions)
 * @param version    version label, e.g. {@code "v1"}
 * @param beanClass  the concrete class registered for this version
 * @param bean       the Spring bean instance (may be {@code null} during AOT)
 * @since 1.4.2
 */
public record VersionedCell(
        String cellName,
        String version,
        Class<?> beanClass,
        Object bean
) {
    /**
     * Snapshot of the current traffic-split state for a cell.
     *
     * @param cellName logical cell name
     * @param versions available versions with their respective weights
     * @param activeVersion the version currently receiving most traffic
     */
    public record TrafficSplitInfo(
            String cellName,
            Map<String, Integer> versions,
            String activeVersion
    ) {}

    /**
     * Describes a specific version of a cell for the admin API.
     *
     * @param cellName logical cell name
     * @param version  version label
     * @param className fully-qualified class name
     * @param weight    current traffic weight (0–100)
     * @param active    whether this version is receiving traffic
     */
    public record VersionInfo(
            String cellName,
            String version,
            String className,
            int weight,
            boolean active
    ) {}
}
