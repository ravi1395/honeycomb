package com.honeycomb.core.web;

import com.honeycomb.core.util.HoneycombConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration tests for batch, async, and admin shared method endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@SuppressWarnings("null")
public class SharedwallBatchAndAdminIntegrationTest {

    private static final String SHARED_USER = "shared";
    private static final String SHARED_PASSWORD = "changeit";

    @Autowired
    private WebTestClient webClient;

    // ──────────── Batch endpoint tests ──────────────

    @Test
    void batchInvokeMultipleMethods() {
        String batchBody = """
                [
                  {"methodName": "echo", "version": "v1", "body": null},
                  {"methodName": "summarize", "version": "v1", "body": {"key": "value"}}
                ]
                """;

        webClient.post().uri("/honeycomb/shared/batch")
                .headers(h -> {
                    h.setBasicAuth(SHARED_USER, SHARED_PASSWORD);
                    h.add("X-From-Cell", "test-client");
                })
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(batchBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].methodName").isEqualTo("echo")
                .jsonPath("$[1].methodName").isEqualTo("summarize");
    }

    @Test
    void batchInvokeEmptyListReturnsBadRequest() {
        webClient.post().uri("/honeycomb/shared/batch")
                .headers(h -> h.setBasicAuth(SHARED_USER, SHARED_PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("[]")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void batchInvokeWithNonexistentMethod() {
        String batchBody = """
                [
                  {"methodName": "echo", "version": "v1"},
                  {"methodName": "doesNotExist", "version": "v1"}
                ]
                """;

        webClient.post().uri("/honeycomb/shared/batch")
                .headers(h -> {
                    h.setBasicAuth(SHARED_USER, SHARED_PASSWORD);
                    h.add("X-From-Cell", "test-client");
                })
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(batchBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].status").isEqualTo("ok")
                .jsonPath("$[1].status").isEqualTo("error");
    }

    // ──────────── Async endpoint tests ──────────────

    @Test
    void asyncInvokeReturnsAccepted() {
        webClient.post().uri("/honeycomb/shared/async/echo")
                .headers(h -> {
                    h.setBasicAuth(SHARED_USER, SHARED_PASSWORD);
                    h.add("X-From-Cell", "test-client");
                })
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("hello-async")
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.trackingId").isNotEmpty()
                .jsonPath("$.status").isEqualTo("accepted")
                .jsonPath("$.method").isEqualTo("echo");
    }

    @Test
    void asyncInvokeWithVersion() {
        webClient.post().uri("/honeycomb/shared/async/echo")
                .headers(h -> {
                    h.setBasicAuth(SHARED_USER, SHARED_PASSWORD);
                    h.add("X-From-Cell", "test-client");
                    h.add(HoneycombConstants.Headers.SHARED_VERSION, "v2");
                })
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("hello-v2")
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.trackingId").isNotEmpty();
    }

    // ──────────── Admin endpoint tests ──────────────

    @Test
    void adminListRegisteredMethods() {
        webClient.get().uri("/honeycomb/admin/shared/methods")
                .headers(h -> h.setBasicAuth(SHARED_USER, SHARED_PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").value(len -> {
                    assert ((int) len) > 0 : "Expected at least one registered method";
                });
    }

    @Test
    void adminListCircuitBreakers() {
        webClient.get().uri("/honeycomb/admin/shared/circuit-breakers")
                .headers(h -> h.setBasicAuth(SHARED_USER, SHARED_PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray();
    }

    @Test
    void adminGetCircuitBreakerForMethod() {
        // First make a call so the circuit breaker gets created
        webClient.post().uri("/honeycomb/shared/echo")
                .headers(h -> {
                    h.setBasicAuth(SHARED_USER, SHARED_PASSWORD);
                    h.add("X-From-Cell", "test-client");
                })
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("warmup")
                .exchange()
                .expectStatus().isOk();

        // Now check the circuit breaker
        webClient.get().uri("/honeycomb/admin/shared/circuit-breakers/echo/v1")
                .headers(h -> h.setBasicAuth(SHARED_USER, SHARED_PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("shared-method@echo:v1")
                .jsonPath("$.state").isNotEmpty();
    }

    @Test
    void adminResetCircuitBreaker() {
        webClient.post().uri("/honeycomb/admin/shared/circuit-breakers/echo/v1/reset")
                .headers(h -> h.setBasicAuth(SHARED_USER, SHARED_PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ok");
    }

    @Test
    void adminCacheInfo() {
        webClient.get().uri("/honeycomb/admin/shared/cache")
                .headers(h -> h.setBasicAuth(SHARED_USER, SHARED_PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.methodCount").isNotEmpty()
                .jsonPath("$.lastRefreshDurationMs").isNotEmpty()
                .jsonPath("$.methodCandidateCounts").isNotEmpty();
    }

    // ──────────── Idempotency test ──────────────

    @Test
    void dispatchWithIdempotencyKeyHeader() {
        // The idempotency service needs to be enabled via config.
        // By default it's disabled, so this test verifies the header
        // is accepted without error (passthrough behaviour).
        webClient.post().uri("/honeycomb/shared/echo")
                .headers(h -> {
                    h.setBasicAuth(SHARED_USER, SHARED_PASSWORD);
                    h.add("X-From-Cell", "test-client");
                    h.add(HoneycombConstants.Headers.IDEMPOTENCY_KEY, "test-key-123");
                })
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("hello-idempotent")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ExampleSharedService.result").isEqualTo("echo:hello-idempotent");
    }
}
