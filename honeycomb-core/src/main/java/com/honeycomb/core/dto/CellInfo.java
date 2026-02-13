package com.honeycomb.core.dto;

import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Immutable DTO describing a registered cell.
 *
 * @param name          exposed cell name
 * @param className     fully-qualified class name
 * @param fields        list of field descriptors
 * @param sharedMethods list of shared method descriptors
 */
public record CellInfo(
        @NonNull String name,
        @NonNull String className,
        @NonNull List<FieldInfo> fields,
        @NonNull List<SharedMethodInfo> sharedMethods
) {
    public CellInfo {
        name = Objects.requireNonNull(Objects.requireNonNullElse(name, ""));
        className = Objects.requireNonNull(Objects.requireNonNullElse(className, ""));
        fields = Objects.requireNonNull(Objects.requireNonNullElse(fields, List.of()));
        sharedMethods = Objects.requireNonNull(Objects.requireNonNullElse(sharedMethods, List.of()));
    }

    /**
     * Field descriptor.
     *
     * @param name field name
     * @param type fully-qualified type
     */
    public record FieldInfo(
            @NonNull String name,
            @NonNull String type
    ) {
        public FieldInfo {
            name = Objects.requireNonNull(Objects.requireNonNullElse(name, ""));
            type = Objects.requireNonNull(Objects.requireNonNullElse(type, "java.lang.Object"));
        }
    }
}
