package com.example.honeycomb.dto;

import com.example.honeycomb.util.HoneycombConstants;
import org.springframework.lang.NonNull;

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
        if (error == null) error = HoneycombConstants.Messages.UNKNOWN;
        if (message == null) message = HoneycombConstants.Messages.EMPTY;
    }

    /** Convenience factory for single-field errors. */
    public static ErrorResponse of(@NonNull String error) {
        return new ErrorResponse(error, HoneycombConstants.Messages.EMPTY);
    }
}
