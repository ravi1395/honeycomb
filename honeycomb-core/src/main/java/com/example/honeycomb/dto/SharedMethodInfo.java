package com.example.honeycomb.dto;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Immutable DTO describing a shared method exposed via @Sharedwall.
 *
 * @param name        method name
 * @param returnType  fully-qualified return type
 * @param parameters  list of parameter descriptors
 * @param allowedFrom list of allowed callers (empty = unrestricted)
 */
public record SharedMethodInfo(
        @NonNull String name,
        @NonNull String returnType,
        @NonNull List<ParameterInfo> parameters,
        @NonNull List<String> allowedFrom
) {
    public SharedMethodInfo {
        name = Objects.requireNonNull(Objects.requireNonNullElse(name, ""));
        returnType = Objects.requireNonNull(Objects.requireNonNullElse(returnType, "void"));
        parameters = Objects.requireNonNull(Objects.requireNonNullElse(parameters, List.of()));
        allowedFrom = Objects.requireNonNull(Objects.requireNonNullElse(allowedFrom, List.of()));
    }

    /**
     * Parameter descriptor.
     *
     * @param name parameter name (requires -parameters compiler flag)
     * @param type fully-qualified type
     */
    public record ParameterInfo(
            @Nullable String name,
            @NonNull String type
    ) {
        public ParameterInfo {
            type = Objects.requireNonNull(Objects.requireNonNullElse(type, "java.lang.Object"));
        }
    }
}
