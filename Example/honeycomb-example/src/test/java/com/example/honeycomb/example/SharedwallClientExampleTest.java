package com.example.honeycomb.example;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SharedwallClientExampleTest {

    @Test
    void unwrapDiscountMapsValidEnvelope() {
        SharedwallClientExample example = new SharedwallClientExample(
                WebClient.builder(),
                mock(ReactiveOAuth2AuthorizedClientManager.class)
        );

        Map<String, Object> envelope = Map.of(
                ExampleConstants.Cells.PRICING,
                Map.of("result", Map.of(
                        ExampleConstants.JsonKeys.CURRENCY, "USD",
                        ExampleConstants.JsonKeys.LIST_PRICE, "49.99",
                        ExampleConstants.JsonKeys.DISCOUNT_PCT, "0.12",
                        ExampleConstants.JsonKeys.DISCOUNTED, "43.99"
                ))
        );

        SharedwallClientExample.DiscountResult result = example.unwrapDiscount(envelope);

        assertEquals("USD", result.currency());
        assertEquals(new BigDecimal("49.99"), result.listPrice());
        assertEquals(new BigDecimal("0.12"), result.discountPct());
        assertEquals(new BigDecimal("43.99"), result.discounted());
    }

    @Test
    void unwrapDiscountReturnsDefaultsWhenEnvelopeMissing() {
        SharedwallClientExample example = new SharedwallClientExample(
                WebClient.builder(),
                mock(ReactiveOAuth2AuthorizedClientManager.class)
        );

        SharedwallClientExample.DiscountResult result = example.unwrapDiscount(Map.of());

        assertEquals(ExampleConstants.Values.USD, result.currency());
        assertTrue(result.listPrice().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(result.discountPct().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(result.discounted().compareTo(BigDecimal.ZERO) == 0);
    }
}
