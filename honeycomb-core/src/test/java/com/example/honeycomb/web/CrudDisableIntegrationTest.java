package com.example.honeycomb.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import java.util.Objects;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class CrudDisableIntegrationTest {

    @Autowired
    private WebTestClient webClient;

    @Autowired
    private com.example.honeycomb.config.HoneycombProperties props;

    @Test
    void createIsDisabledForSampleModel() {
        webClient.post().uri("/honeycomb/models/SampleModel/items")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .bodyValue("{\"name\":\"x\"}")
                .exchange()
                .expectStatus().isEqualTo(405);
    }

    @Test
    void deleteIsDisabledGlobally() {
        // sanity-check the bound properties
        System.out.println("disabledOperations=" + props.getDisabledOperations());
        org.assertj.core.api.Assertions.assertThat(props.isOperationAllowed("OtherModel", "delete")).isFalse();
        // create a new item under OtherModel (create should be allowed)
        var created = webClient.post().uri("/honeycomb/models/OtherModel/items")
            .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
            .bodyValue("{\"name\":\"to-delete\"}")
            .exchange()
            .expectStatus().isCreated()
            .expectBody(Map.class)
            .returnResult()
            .getResponseBody();

        Map<?, ?> createdNonNull = Objects.requireNonNull(created, "expected created response body");
        String id = (String) createdNonNull.get("id");

        webClient.delete().uri("/honeycomb/models/OtherModel/items/" + id)
                .exchange()
                .expectStatus().isEqualTo(405);
    }
}
