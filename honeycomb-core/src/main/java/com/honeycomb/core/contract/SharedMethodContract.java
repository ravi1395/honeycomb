package com.honeycomb.core.contract;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable descriptor for a single shared-method contract.
 *
 * <p>Captures the method name, version, parameter schema, return type,
 * access constraints, and example request/response pairs. Used both for
 * code-generation and for runtime verification.</p>
 *
 * @since 1.4.3
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SharedMethodContract(
        /** Shared method name (or alias). */
        String methodName,
        /** Contract version ({@code v1}, {@code v2}, …). */
        String version,
        /** Fully-qualified declaring class. */
        String declaringClass,
        /** Ordered list of parameter descriptors. */
        List<ParamDescriptor> parameters,
        /** Fully-qualified return type (e.g. {@code reactor.core.publisher.Mono<java.lang.String>}). */
        String returnType,
        /** Cells allowed to call this method (empty = all). */
        List<String> allowedFrom,
        /** Example request bodies for stub generation. */
        List<Map<String, Object>> exampleRequests,
        /** Example response bodies for stub generation. */
        List<Map<String, Object>> exampleResponses
) {

    /**
     * Describes a single method parameter.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ParamDescriptor(
            String name,
            String type,
            boolean required
    ) {}

    /** Convenience builder. */
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String methodName;
        private String version = "v1";
        private String declaringClass;
        private final List<ParamDescriptor> parameters = new ArrayList<>();
        private String returnType;
        private final List<String> allowedFrom = new ArrayList<>();
        private final List<Map<String, Object>> exampleRequests = new ArrayList<>();
        private final List<Map<String, Object>> exampleResponses = new ArrayList<>();

        public Builder methodName(String v) { this.methodName = v; return this; }
        public Builder version(String v) { this.version = v; return this; }
        public Builder declaringClass(String v) { this.declaringClass = v; return this; }
        public Builder addParameter(String name, String type, boolean required) {
            parameters.add(new ParamDescriptor(name, type, required));
            return this;
        }
        public Builder returnType(String v) { this.returnType = v; return this; }
        public Builder addAllowedFrom(String cell) { allowedFrom.add(cell); return this; }
        public Builder addExampleRequest(Map<String, Object> ex) {
            exampleRequests.add(new LinkedHashMap<>(ex));
            return this;
        }
        public Builder addExampleResponse(Map<String, Object> ex) {
            exampleResponses.add(new LinkedHashMap<>(ex));
            return this;
        }

        public SharedMethodContract build() {
            return new SharedMethodContract(
                    methodName, version, declaringClass,
                    List.copyOf(parameters), returnType,
                    List.copyOf(allowedFrom),
                    List.copyOf(exampleRequests),
                    List.copyOf(exampleResponses)
            );
        }
    }
}
