package com.honeycomb.core.dto;

import com.honeycomb.core.util.HoneycombConstants;
import org.springframework.lang.NonNull;

import java.util.Objects;

/**
 * Immutable DTO for structured error responses.
 *
 * @param error   short error code (never null)
 * @param message detailed message (never null, may be empty)
 */
public record ErrorResponse(
        @NonNull String error,
        @NonNull String message
) {
    public ErrorResponse {
        error = Objects.requireNonNull(Objects.requireNonNullElse(error, HoneycombConstants.Messages.UNKNOWN));
        message = Objects.requireNonNull(Objects.requireNonNullElse(message, HoneycombConstants.Messages.EMPTY));
    }

    /** Convenience factory for single-field errors. */
    public static ErrorResponse of(@NonNull String error) {
        return new ErrorResponse(error, HoneycombConstants.Messages.EMPTY);
    }
}
