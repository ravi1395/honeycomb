package com.example.honeycomb.example;

import com.example.honeycomb.client.SharedwallCall;
import com.example.honeycomb.client.SharedwallClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
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
        SharedwallClient sharedwallClient = SharedwallClient.builder(webClient, baseUrl)
                .fromCell(ExampleConstants.Shared.DEMO_CALLER)
                .registrationId(registrationId)
                .build();

        PricingSharedMethods api = sharedwallClient.createTypedClient(PricingSharedMethods.class, true);

        return api.discount(body)
                .map(this::unwrapDiscount)
                .doOnNext(resp -> log.info(ExampleConstants.Messages.LOG_SHARED_DISCOUNT_UTIL, resp))
                .then();
    }

    DiscountResult unwrapDiscount(Map<String, Object> envelope) {
        Object byCell = envelope.get(ExampleConstants.Cells.PRICING);
        if (!(byCell instanceof Map<?, ?> cellMapObj)) {
            return new DiscountResult(ExampleConstants.Values.USD, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        Object resultObj = cellMapObj.get("result");
        if (!(resultObj instanceof Map<?, ?> resultMapObj)) {
            return new DiscountResult(ExampleConstants.Values.USD, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        String currency = asString(resultMapObj.get(ExampleConstants.JsonKeys.CURRENCY), ExampleConstants.Values.USD);
        BigDecimal listPrice = asDecimal(resultMapObj.get(ExampleConstants.JsonKeys.LIST_PRICE));
        BigDecimal discountPct = asDecimal(resultMapObj.get(ExampleConstants.JsonKeys.DISCOUNT_PCT));
        BigDecimal discounted = asDecimal(resultMapObj.get(ExampleConstants.JsonKeys.DISCOUNTED));
        return new DiscountResult(currency, listPrice, discountPct, discounted);
    }

    private BigDecimal asDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private String asString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString();
        return text.isBlank() ? fallback : text;
    }

    interface PricingSharedMethods {
        @SharedwallCall(ExampleConstants.Values.ROUTE_DISCOUNT)
        Mono<Map<String, Object>> discount(Map<String, Object> payload);
    }

    record DiscountResult(String currency, BigDecimal listPrice, BigDecimal discountPct, BigDecimal discounted) {}
}
