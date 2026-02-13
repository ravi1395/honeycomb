package com.example.honeycomb.web;

import com.example.honeycomb.util.HoneycombConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class SharedwallStubEndpointIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void stubEndpointReturnsGeneratedInterfaceWithCustomNames() {
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(HoneycombConstants.Paths.HONEYCOMB_SHARED + "/methods/stub")
                        .queryParam("interfaceName", "PricingApi")
                        .queryParam("packageName", "com.example.generated")
                        .build())
                .headers(h -> h.setBasicAuth("shared", "changeit"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_PLAIN_VALUE)
                .expectBody(String.class)
                .value(body -> {
                    assert body.contains("package com.example.generated;");
                    assert body.contains("public interface PricingApi");
                    assert body.contains("@SharedwallCall(\"echo\")");
                    assert body.contains("Mono<Object> echo(Object body);");
                });
    }

    @Test
    void stubEndpointUsesDefaultsWhenNoQueryParamsProvided() {
        webTestClient.get()
                .uri(HoneycombConstants.Paths.HONEYCOMB_SHARED + "/methods/stub")
                .headers(h -> h.setBasicAuth("shared", "changeit"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_PLAIN_VALUE)
                .expectBody(String.class)
                .value(body -> {
                    assert body.contains("package com.example.honeycomb.client.generated;");
                    assert body.contains("public interface SharedwallApi");
                });
    }
}
