package com.example.honeycomb.web;

import com.example.honeycomb.dto.ErrorCode;
import com.example.honeycomb.dto.ErrorResponse;
import com.example.honeycomb.util.HoneycombConstants;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.resource.NoResourceFoundException;

import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ErrorHandlerAdvice {
    private static final Logger log = LoggerFactory.getLogger(ErrorHandlerAdvice.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(@NonNull IllegalArgumentException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : HoneycombConstants.Messages.EMPTY;
        log.warn(HoneycombConstants.Messages.LOG_BAD_REQUEST, msg, ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorCode.BAD_REQUEST.toResponse(msg));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(@NonNull ConstraintViolationException ex) {
        String violations = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath()
                        + HoneycombConstants.Names.SEPARATOR_COLON
                        + HoneycombConstants.Messages.SPACE
                        + v.getMessage())
                .collect(Collectors.joining(HoneycombConstants.Names.LIST_SEPARATOR));
        log.warn(HoneycombConstants.Messages.LOG_VALIDATION_ERROR, violations);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorCode.VALIDATION_ERROR.toResponse(violations));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(@NonNull WebExchangeBindException ex) {
        String errors = ex.getFieldErrors().stream()
                .map(e -> e.getField()
                        + HoneycombConstants.Names.SEPARATOR_COLON
                        + HoneycombConstants.Messages.SPACE
                        + e.getDefaultMessage())
                .collect(Collectors.joining(HoneycombConstants.Names.LIST_SEPARATOR));
        log.warn(HoneycombConstants.Messages.LOG_BINDING_ERROR, errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorCode.VALIDATION_ERROR.toResponse(errors));
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleCircuitOpen(@NonNull CallNotPermittedException ex) {
        log.warn(HoneycombConstants.Messages.LOG_CIRCUIT_OPEN, ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorCode.CIRCUIT_OPEN.toResponse());
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ErrorResponse> handleRateLimited(@NonNull RequestNotPermitted ex) {
        log.warn(HoneycombConstants.Messages.LOG_RATE_LIMIT, ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorCode.RATE_LIMITED.toResponse());
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ErrorResponse> handleTimeout(@NonNull TimeoutException ex) {
        log.warn(HoneycombConstants.Messages.LOG_TIMEOUT, ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(ErrorCode.TIMEOUT.toResponse());
    }

        @ExceptionHandler(NoResourceFoundException.class)
        public ResponseEntity<Void> handleNoResource(@NonNull NoResourceFoundException ex) {
                log.debug(HoneycombConstants.Messages.LOG_RESOURCE_NOT_FOUND, ex.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        String msg = (ex != null && ex.getMessage() != null)
                ? ex.getMessage()
                : HoneycombConstants.Messages.EMPTY;
        log.error(HoneycombConstants.Messages.LOG_UNHANDLED, msg, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorCode.INTERNAL_ERROR.toResponse(msg));
    }
}
