package com.example.honeycomb.dto;

import org.springframework.lang.NonNull;

/**
 * Cell-specific error codes for structured API responses.
 */
public enum ErrorCode {
    // General errors
    INTERNAL_ERROR("internal-error", "An unexpected error occurred"),
    BAD_REQUEST("bad-request", "Invalid request format or parameters"),
    VALIDATION_ERROR("validation-error", "Request validation failed"),
    
    // Cell errors
    CELL_NOT_FOUND("cell-not-found", "The requested cell was not found"),
    METHOD_NOT_FOUND("method-not-found", "The requested shared method was not found"),
    METHOD_ACCESS_DENIED("method-access-denied", "Caller is not authorized to invoke this method"),
    
    // CRUD operation errors
    OPERATION_DISABLED("operation-disabled", "This operation is disabled for the cell"),
    ITEM_NOT_FOUND("item-not-found", "The requested item was not found"),
    ITEM_CREATE_FAILED("item-create-failed", "Failed to create the item"),
    
    // Resilience errors
    CIRCUIT_OPEN("circuit-open", "Service temporarily unavailable due to circuit breaker"),
    RATE_LIMITED("rate-limited", "Too many requests - rate limit exceeded"),
    TIMEOUT("timeout", "Request timed out"),
    
    // Forwarding errors
    FORWARD_FAILED("forward-failed", "Failed to forward request to remote cell"),
    JSON_PARSE_ERROR("json-parse-error", "Failed to parse JSON payload");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @NonNull
    public String getCode() {
        return code;
    }

    @NonNull
    public String getDefaultMessage() {
        return defaultMessage;
    }

    /**
     * Create an ErrorResponse with the default message.
     */
    public ErrorResponse toResponse() {
        return new ErrorResponse(code, defaultMessage);
    }

    /**
     * Create an ErrorResponse with a custom message.
     */
    public ErrorResponse toResponse(@NonNull String customMessage) {
        return new ErrorResponse(code, customMessage);
    }
}
