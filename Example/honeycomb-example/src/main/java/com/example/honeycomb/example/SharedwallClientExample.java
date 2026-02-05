package com.example.honeycomb.example;

import com.example.honeycomb.util.HoneycombConstants;
import com.example.honeycomb.client.SharedwallClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@ConditionalOnBean(ReactiveOAuth2AuthorizedClientManager.class)
public class SharedwallClientExample {
    private static final Logger log = LoggerFactory.getLogger(SharedwallClientExample.class);

        private final WebClient webClient;

    @Value(ExampleConstants.PropertyValues.OAUTH2_REGISTRATION_ID)
    private String registrationId;

    public SharedwallClientExample(WebClient.Builder builder,
                                   ReactiveOAuth2AuthorizedClientManager clientManager) {
        ServerOAuth2AuthorizedClientExchangeFilterFunction oauth2 =
                new ServerOAuth2AuthorizedClientExchangeFilterFunction(clientManager);
        this.webClient = builder.filter(oauth2).build();
    }

    public Mono<Void> callDiscount(String baseUrl) {
        Map<String, Object> body = Map.of(
                ExampleConstants.JsonKeys.LIST_PRICE, 49.99,
                ExampleConstants.JsonKeys.DISCOUNT_PCT, 0.12
        );
        return SharedwallClient.builder(webClient, baseUrl)
                .fromCell(ExampleConstants.Shared.DEMO_CALLER)
                .registrationId(registrationId)
                .build()
                .invoke(ExampleConstants.Values.ROUTE_DISCOUNT, body, MediaType.APPLICATION_JSON)
                .doOnNext(resp -> log.info(ExampleConstants.Messages.LOG_SHARED_DISCOUNT_UTIL, resp))
                .then();
    }

    public Mono<Void> callDiscountViaCells(String baseUrl) {
        Map<String, Object> body = Map.of(
                ExampleConstants.JsonKeys.LIST_PRICE, 49.99,
                ExampleConstants.JsonKeys.DISCOUNT_PCT, 0.10
        );
        String url = baseUrl
                + HoneycombConstants.Paths.CELLS_BASE
                + HoneycombConstants.Names.SEPARATOR_SLASH
                + ExampleConstants.Shared.DEMO_CALLER
                + HoneycombConstants.Names.SEPARATOR_SLASH
                + HoneycombConstants.Paths.INVOKE
                + HoneycombConstants.Names.SEPARATOR_SLASH
                + ExampleConstants.Cells.PRICING
                + HoneycombConstants.Names.SEPARATOR_SLASH
                + HoneycombConstants.Paths.SHARED
                + HoneycombConstants.Names.SEPARATOR_SLASH
                + ExampleConstants.Values.ROUTE_DISCOUNT
                + "?" + HoneycombConstants.Params.POLICY + "=" + ExampleConstants.Query.POLICY_ROUND_ROBIN;

        return webClient.post().uri(url)
                .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId(registrationId))
                .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(resp -> log.info(ExampleConstants.Messages.LOG_CELLS_DISCOUNT, resp))
                .then();
    }
}
