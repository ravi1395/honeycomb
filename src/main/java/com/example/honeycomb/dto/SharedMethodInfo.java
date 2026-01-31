package com.example.honeycomb.dto;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.List;

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
        if (name == null) name = "";
        if (returnType == null) returnType = "void";
        if (parameters == null) parameters = List.of();
        if (allowedFrom == null) allowedFrom = List.of();
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
            if (type == null) type = "java.lang.Object";
        }
    }
}
