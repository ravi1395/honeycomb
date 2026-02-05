package com.example.honeycomb.dto;

import org.springframework.lang.NonNull;
import com.example.honeycomb.util.HoneycombConstants;

/**
 * Cell-specific error codes for structured API responses.
 */
public enum ErrorCode {
    // General errors
    INTERNAL_ERROR(HoneycombConstants.ErrorCodes.INTERNAL_ERROR, HoneycombConstants.ErrorMessages.INTERNAL_ERROR),
    BAD_REQUEST(HoneycombConstants.ErrorCodes.BAD_REQUEST, HoneycombConstants.ErrorMessages.BAD_REQUEST),
    VALIDATION_ERROR(HoneycombConstants.ErrorCodes.VALIDATION_ERROR, HoneycombConstants.ErrorMessages.VALIDATION_ERROR),
    
    // Cell errors
    CELL_NOT_FOUND(HoneycombConstants.ErrorCodes.CELL_NOT_FOUND, HoneycombConstants.ErrorMessages.CELL_NOT_FOUND),
    METHOD_NOT_FOUND(HoneycombConstants.ErrorCodes.METHOD_NOT_FOUND, HoneycombConstants.ErrorMessages.METHOD_NOT_FOUND),
    METHOD_ACCESS_DENIED(HoneycombConstants.ErrorCodes.METHOD_ACCESS_DENIED, HoneycombConstants.ErrorMessages.METHOD_ACCESS_DENIED),
    
    // CRUD operation errors
    OPERATION_DISABLED(HoneycombConstants.ErrorCodes.OPERATION_DISABLED, HoneycombConstants.ErrorMessages.OPERATION_DISABLED),
    ITEM_NOT_FOUND(HoneycombConstants.ErrorCodes.ITEM_NOT_FOUND, HoneycombConstants.ErrorMessages.ITEM_NOT_FOUND),
    ITEM_CREATE_FAILED(HoneycombConstants.ErrorCodes.ITEM_CREATE_FAILED, HoneycombConstants.ErrorMessages.ITEM_CREATE_FAILED),
    
    // Resilience errors
    CIRCUIT_OPEN(HoneycombConstants.ErrorCodes.CIRCUIT_OPEN, HoneycombConstants.ErrorMessages.CIRCUIT_OPEN),
    RATE_LIMITED(HoneycombConstants.ErrorCodes.RATE_LIMITED, HoneycombConstants.ErrorMessages.RATE_LIMITED),
    TIMEOUT(HoneycombConstants.ErrorCodes.TIMEOUT, HoneycombConstants.ErrorMessages.TIMEOUT),
    
    // Forwarding errors
    FORWARD_FAILED(HoneycombConstants.ErrorCodes.FORWARD_FAILED, HoneycombConstants.ErrorMessages.FORWARD_FAILED),
    JSON_PARSE_ERROR(HoneycombConstants.ErrorCodes.JSON_PARSE_ERROR, HoneycombConstants.ErrorMessages.JSON_PARSE_ERROR);

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
