package com.example.honeycomb.dto;

import org.springframework.lang.NonNull;

import java.util.List;

/**
 * Immutable DTO describing a registered domain/cell.
 *
 * @param name          exposed domain name
 * @param className     fully-qualified class name
 * @param fields        list of field descriptors
 * @param sharedMethods list of shared method descriptors
 */
public record DomainInfo(
        @NonNull String name,
        @NonNull String className,
        @NonNull List<FieldInfo> fields,
        @NonNull List<SharedMethodInfo> sharedMethods
) {
    public DomainInfo {
        if (name == null) name = "";
        if (className == null) className = "";
        if (fields == null) fields = List.of();
        if (sharedMethods == null) sharedMethods = List.of();
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
            if (name == null) name = "";
            if (type == null) type = "java.lang.Object";
        }
    }
}
