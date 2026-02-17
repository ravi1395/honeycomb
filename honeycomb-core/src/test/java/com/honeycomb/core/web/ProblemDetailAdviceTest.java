package com.honeycomb.core.web;

import com.honeycomb.core.dto.ProblemDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ProblemDetailAdvice} — RFC 7807 error mapping.
 *
 * @since 1.4.3
 */
class ProblemDetailAdviceTest {

    private ProblemDetailAdvice advice;
    private MockServerWebExchange exchange;

    @BeforeEach
    void setUp() {
        advice = new ProblemDetailAdvice();
        MockServerHttpRequest request = MockServerHttpRequest.get("/honeycomb/cells/test")
                .header("X-Request-Id", "req-abc-123")
                .build();
        exchange = MockServerWebExchange.from(request);
    }

    @Test
    @DisplayName("IllegalArgumentException maps to 400 with problem+json")
    void badRequest() {
        ResponseEntity<ProblemDetail> response = advice.handleBadRequest(
                new IllegalArgumentException("invalid cell name"), exchange);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertTrue(response.getBody().detail().contains("invalid cell name"));
        assertEquals("application/problem+json", response.getHeaders().getContentType().toString());
    }

    @Test
    @DisplayName("TimeoutException maps to 504 Gateway Timeout")
    void timeout() {
        ResponseEntity<ProblemDetail> response = advice.handleTimeout(
                new TimeoutException("5000ms deadline exceeded"), exchange);

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(504, response.getBody().status());
        assertTrue(response.getBody().detail().contains("deadline exceeded"));
    }

    @Test
    @DisplayName("generic Exception maps to 500 Internal Server Error")
    void internalError() {
        ResponseEntity<ProblemDetail> response = advice.handleGeneric(
                new RuntimeException("unexpected"), exchange);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().status());
    }

    @Test
    @DisplayName("requestId is included in extensions when present")
    void requestIdInExtensions() {
        ResponseEntity<ProblemDetail> response = advice.handleBadRequest(
                new IllegalArgumentException("test"), exchange);

        assertNotNull(response.getBody());
        assertNotNull(response.getBody().extensions());
        assertEquals("req-abc-123", response.getBody().extensions().get("requestId"));
    }

    @Test
    @DisplayName("ProblemDetail has RFC 7807 type URN")
    void typeUrn() {
        ResponseEntity<ProblemDetail> response = advice.handleBadRequest(
                new IllegalArgumentException("test"), exchange);

        assertNotNull(response.getBody());
        assertNotNull(response.getBody().type());
        assertTrue(response.getBody().type().toString().startsWith("urn:honeycomb:error:"));
    }

    @Test
    @DisplayName("ProblemDetail includes instance path")
    void instancePath() {
        ResponseEntity<ProblemDetail> response = advice.handleBadRequest(
                new IllegalArgumentException("test"), exchange);

        assertNotNull(response.getBody());
        assertNotNull(response.getBody().instance());
        assertTrue(response.getBody().instance().toString().contains("/honeycomb/cells/test"));
    }

    @Test
    @DisplayName("null exception message does not NPE")
    void nullExceptionMessage() {
        ResponseEntity<ProblemDetail> response = advice.handleGeneric(
                new RuntimeException((String) null), exchange);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}
