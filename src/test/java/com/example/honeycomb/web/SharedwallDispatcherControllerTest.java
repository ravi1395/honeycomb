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
                .headers(h -> h.setBasicAuth("shared", "changeit"))
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("hello")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ExampleSharedService.result").isEqualTo("echo:hello");
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
}
