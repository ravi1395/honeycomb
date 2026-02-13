package com.example.honeycomb.dto;

import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * DTO describing an invokable sharedwall API method.
 *
 * @param cellName    cell class simple name exposing the method
 * @param methodName  shared method alias/name
 * @param path        invoke path under honeycomb
 * @param returnType  fully-qualified return type
 * @param parameters  parameter descriptors
 * @param allowedFrom allowed caller cells (empty = unrestricted)
 * @param version     contract version label
 * @param deprecated  whether method is deprecated
 */
public record SharedwallInvokeInfo(
        @NonNull String cellName,
        @NonNull String methodName,
        @NonNull String path,
        @NonNull String returnType,
        @NonNull List<SharedMethodInfo.ParameterInfo> parameters,
    @NonNull List<String> allowedFrom,
    @NonNull String version,
    boolean deprecated
) {
    public SharedwallInvokeInfo {
        cellName = Objects.requireNonNull(Objects.requireNonNullElse(cellName, ""));
        methodName = Objects.requireNonNull(Objects.requireNonNullElse(methodName, ""));
        path = Objects.requireNonNull(Objects.requireNonNullElse(path, ""));
        returnType = Objects.requireNonNull(Objects.requireNonNullElse(returnType, "void"));
        parameters = Objects.requireNonNull(Objects.requireNonNullElse(parameters, List.of()));
        allowedFrom = Objects.requireNonNull(Objects.requireNonNullElse(allowedFrom, List.of()));
        version = Objects.requireNonNull(Objects.requireNonNullElse(version, "v1"));
    }
}
