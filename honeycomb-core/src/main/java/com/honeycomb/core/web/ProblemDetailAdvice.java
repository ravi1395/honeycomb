package com.honeycomb.core.web;

import com.honeycomb.core.dto.ErrorCode;
import com.honeycomb.core.dto.ProblemDetail;
import com.honeycomb.core.util.HoneycombConstants;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.server.ServerWebExchange;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Global {@link RestControllerAdvice} that maps common exceptions to
 * RFC 7807 {@code application/problem+json} responses.
 *
 * <p>Produces {@link ProblemDetail} payloads for all error paths,
 * conforming to <a href="https://www.rfc-editor.org/rfc/rfc7807">RFC 7807</a>.</p>
 *
 * @since 1.4.3 — migrated from custom {@code ErrorResponse} to RFC 7807
 */
@RestControllerAdvice
public class ProblemDetailAdvice {
    private static final Logger log = LoggerFactory.getLogger(ProblemDetailAdvice.class);
    private static final MediaType PROBLEM_JSON = MediaType.valueOf("application/problem+json");

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(@NonNull IllegalArgumentException ex,
                                                           ServerWebExchange exchange) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        log.warn(HoneycombConstants.Messages.LOG_BAD_REQUEST, msg, ex);
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, msg, exchange);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleValidation(@NonNull ConstraintViolationException ex,
                                                           ServerWebExchange exchange) {
        String violations = Objects.requireNonNull(ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; ")));
        log.warn(HoneycombConstants.Messages.LOG_VALIDATION_ERROR, violations);
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, violations, exchange);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ProblemDetail> handleBindException(@NonNull WebExchangeBindException ex,
                                                              ServerWebExchange exchange) {
        String errors = Objects.requireNonNull(ex.getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; ")));
        log.warn(HoneycombConstants.Messages.LOG_BINDING_ERROR, errors);
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, errors, exchange);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ProblemDetail> handleCircuitOpen(@NonNull CallNotPermittedException ex,
                                                            ServerWebExchange exchange) {
        log.warn(HoneycombConstants.Messages.LOG_CIRCUIT_OPEN, ex.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.CIRCUIT_OPEN, ex.getMessage(), exchange);
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ProblemDetail> handleRateLimited(@NonNull RequestNotPermitted ex,
                                                            ServerWebExchange exchange) {
        log.warn(HoneycombConstants.Messages.LOG_RATE_LIMIT, ex.getMessage());
        return problem(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED, ex.getMessage(), exchange);
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ProblemDetail> handleTimeout(@NonNull TimeoutException ex,
                                                        ServerWebExchange exchange) {
        log.warn(HoneycombConstants.Messages.LOG_TIMEOUT, ex.getMessage());
        return problem(HttpStatus.GATEWAY_TIMEOUT, ErrorCode.TIMEOUT, ex.getMessage(), exchange);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(@NonNull NoResourceFoundException ex,
                                                           ServerWebExchange exchange) {
        log.debug(HoneycombConstants.Messages.LOG_RESOURCE_NOT_FOUND, ex.getMessage());
        return problem(HttpStatus.NOT_FOUND, ErrorCode.ITEM_NOT_FOUND, ex.getMessage(), exchange);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex, ServerWebExchange exchange) {
        String msg = ex != null && ex.getMessage() != null ? ex.getMessage() : "";
        log.error(HoneycombConstants.Messages.LOG_UNHANDLED, msg, ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, msg, exchange);
    }

    // -- helpers ------------------------------------------------------------

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, ErrorCode code,
                                                   String detail, ServerWebExchange exchange) {
        String path = exchange != null
                ? exchange.getRequest().getPath().pathWithinApplication().value()
                : null;
        String requestId = exchange != null
                ? exchange.getRequest().getHeaders().getFirst(HoneycombConstants.Headers.REQUEST_ID)
                : null;

        Map<String, Object> extensions = requestId != null
                ? Map.of("requestId", requestId)
                : null;

        ProblemDetail problem = ProblemDetail.of(
                status.value(), code.getCode(), code.getDefaultMessage(),
                detail, path, extensions
        );

        return ResponseEntity.status(status)
                .contentType(PROBLEM_JSON)
                .body(problem);
    }
}
