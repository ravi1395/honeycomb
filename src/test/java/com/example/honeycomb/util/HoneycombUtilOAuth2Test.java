package com.example.honeycomb.util;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HoneycombUtilOAuth2Test {

    @Test
    void createOAuth2WebClientReturnsClient() {
        ReactiveOAuth2AuthorizedClientManager manager = mock(ReactiveOAuth2AuthorizedClientManager.class);
        WebClient client = HoneycombUtil.createOAuth2WebClient(WebClient.builder(), manager, "sharedwall-client");
        assertThat(client).isNotNull();
    }

    @Test
    void invokeSharedwallOAuth2AddsRegistrationIdAndParsesResponse() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        ExchangeFunction exchange = request -> {
            captured.set(request);
            String body = "{\"ok\":true}";
            ClientResponse response = ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .build();
            return Mono.just(response);
        };

        WebClient client = WebClient.builder().exchangeFunction(exchange).build();

        Mono<Map<String, Object>> result = HoneycombUtil.invokeSharedwallOAuth2(
                client,
                "http://localhost:8080",
                "ping",
                null,
                "demo-client",
                MediaType.APPLICATION_JSON,
                "sharedwall-client"
        );

        StepVerifier.create(result)
                .assertNext(map -> assertThat(map).containsEntry("ok", true))
                .verifyComplete();

        ClientRequest request = captured.get();
        assertThat(request).isNotNull();
        assertThat(request.headers().getFirst(HoneycombConstants.Headers.FROM_CELL)).isEqualTo("demo-client");
        assertThat(request.headers().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);

        Map<String, Object> expected = new HashMap<>();
        ServerOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId("sharedwall-client")
                .accept(expected);
        assertThat(request.attributes()).containsAllEntriesOf(expected);
    }
}
