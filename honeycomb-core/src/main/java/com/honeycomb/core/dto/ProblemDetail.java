package com.honeycomb.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RFC 7807 Problem Detail representation for structured API errors.
 *
 * <p>Conforms to {@code application/problem+json} content type.
 * Fields follow the standard: {@code type}, {@code title}, {@code status},
 * {@code detail}, {@code instance}, plus extensible {@code extensions}.</p>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7807">RFC 7807</a>
 * @since 1.4.3
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetail(
        /** URI reference identifying the problem type. */
        @NonNull URI type,
        /** Short human-readable summary. */
        @NonNull String title,
        /** HTTP status code. */
        int status,
        /** Human-readable explanation specific to this occurrence. */
        @Nullable String detail,
        /** URI reference identifying the specific occurrence (e.g. request path). */
        @Nullable URI instance,
        /** ISO-8601 timestamp of the error. */
        @NonNull Instant timestamp,
        /** Additional machine-readable properties. */
        @Nullable Map<String, Object> extensions
) {
    /** Base URI for Honeycomb problem types. */
    private static final String PROBLEM_BASE = "urn:honeycomb:error:";

    /**
     * Factory for common error creation.
     */
    public static ProblemDetail of(int status, String errorCode, String title, String detail, String path) {
        return new ProblemDetail(
                URI.create(PROBLEM_BASE + errorCode),
                title,
                status,
                detail,
                path != null ? URI.create(path) : null,
                Instant.now(),
                null
        );
    }

    /**
     * Factory with extension properties.
     */
    public static ProblemDetail of(int status, String errorCode, String title,
                                    String detail, String path, Map<String, Object> extensions) {
        return new ProblemDetail(
                URI.create(PROBLEM_BASE + errorCode),
                title,
                status,
                detail,
                path != null ? URI.create(path) : null,
                Instant.now(),
                extensions != null ? new LinkedHashMap<>(extensions) : null
        );
    }

    /**
     * Convert a legacy {@link ErrorCode} to a ProblemDetail.
     */
    public static ProblemDetail fromErrorCode(ErrorCode errorCode, int status, String detail, String path) {
        return of(status, errorCode.getCode(), errorCode.getDefaultMessage(), detail, path);
    }
}
