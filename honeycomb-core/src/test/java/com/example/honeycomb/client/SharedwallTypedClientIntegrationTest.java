package com.example.honeycomb.client;

import com.example.honeycomb.util.HoneycombConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class SharedwallTypedClientIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @LocalServerPort
    private int port;

    @Test
    void sharedMethodsEndpointReturnsDiscoveredMethods() {
        webTestClient.get()
                .uri(HoneycombConstants.Paths.HONEYCOMB_SHARED + "/methods")
                .headers(h -> h.setBasicAuth("shared", "changeit"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[?(@.methodName=='echo')]").exists()
                .jsonPath("$[?(@.methodName=='summarize')]").exists();
    }

    @Test
    void createTypedClientWithValidationSucceedsForCompatibleContract() {
        SharedwallClient client = buildClient();
        SummarizeApi api = client.createTypedClient(SummarizeApi.class, true);

        Map<String, Object> result = api.summarize(Map.of("a", 1, "b", "x")).block();

        @SuppressWarnings("unchecked")
        Map<String, Object> cellPayload = (Map<String, Object>) result.get("ExampleSharedService");
        @SuppressWarnings("unchecked")
        Map<String, Object> methodResult = (Map<String, Object>) cellPayload.get("result");
        assertEquals(2, ((Number) methodResult.get("receivedKeys")).intValue());
    }

    @Test
    void createTypedClientWithValidationFailsForIncompatibleContract() {
        SharedwallClient client = buildClient();

        assertThrows(IllegalStateException.class,
                () -> client.createTypedClient(BadEchoApi.class, true));
    }

    @Test
    void createTypedClientCanAutoUnwrapToString() {
        SharedwallClient client = buildClient();
        EchoApi api = client.createTypedClient(EchoApi.class, true);

        String result = api.echo("hello").block();

        assertEquals("echo:hello", result);
    }

    private SharedwallClient buildClient() {
        WebClient webClient = WebClient.builder()
                .defaultHeaders(h -> h.setBasicAuth("shared", "changeit"))
                .build();

        return SharedwallClient.builder(webClient, "http://localhost:" + port)
                .fromCell("test-client")
                .build();
    }

    interface SummarizeApi {
        Mono<Map<String, Object>> summarize(Map<String, Object> payload);
    }

    interface BadEchoApi {
        Mono<Integer> echo(String input);
    }

    interface EchoApi {
        @SharedwallResult(mode = SharedwallEnvelopeMode.FIRST_RESULT)
        Mono<String> echo(String input);
    }
}
