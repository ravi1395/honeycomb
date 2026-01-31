package com.example.honeycomb.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class SharedwallDispatcherControllerTest {

    @Autowired
    private WebTestClient webClient;

    @Test
    void echoSharedMethod() {
        webClient.post().uri("/honeycomb/shared/echo")
                .headers(h -> { h.setBasicAuth("shared", "changeit"); h.add("X-From-Cell", "test-client"); })
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("hello")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ExampleSharedService.result").isEqualTo("echo:hello");
    }

    @Test
    void echoDeniedWhenCallerNotAllowed() {
        webClient.post().uri("/honeycomb/shared/echo")
                .headers(h -> { h.setBasicAuth("shared", "changeit"); h.add("X-From-Cell", "other-caller"); })
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("hello")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ExampleSharedService.error").value(v -> {
                    // contains access-denied message
                    assert v.toString().contains("access-denied");
                });
    }

    @Test
    void summarizeJsonBinding() {
        webClient.post().uri("/honeycomb/shared/summarize")
                .headers(h -> h.setBasicAuth("shared", "changeit"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"a\":1,\"b\":\"x\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ExampleSharedService.result.receivedKeys").isEqualTo(2);
    }

    @Test
    void concatMultiArg() {
        webClient.post().uri("/honeycomb/shared/concat")
                .headers(h -> h.setBasicAuth("shared", "changeit"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("[\"foo\",\"bar\"]")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ExampleSharedService.result").isEqualTo("foo:bar");
    }

    @Test
    void sumListCollection() {
        webClient.post().uri("/honeycomb/shared/sumList")
                .headers(h -> h.setBasicAuth("shared", "changeit"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("[1,2,3]")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ExampleSharedService.result").isEqualTo(6);
    }

    @Test
    void methodNotFoundReturns404() {
        webClient.post().uri("/honeycomb/shared/doesNotExist")
                .headers(h -> h.setBasicAuth("shared", "changeit"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error").value(v -> {
                    assert v.toString().contains("no shared method");
                });
    }

    @Test
    void malformedJsonReturnsDeserializeError() {
        webClient.post().uri("/honeycomb/shared/summarize")
                .headers(h -> h.setBasicAuth("shared", "changeit"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{notjson")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ExampleSharedService.error").value(v -> {
                    assert v.toString().contains("json-deserialize-error");
                });
    }

    @Test
    void invocationExceptionProducesErrorEntry() {
        webClient.post().uri("/honeycomb/shared/boom")
                .headers(h -> h.setBasicAuth("shared", "changeit"))
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("boom")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ExampleSharedService.error").value(v -> {
                    assert v.toString().contains("boom-exception");
                });
    }
}
