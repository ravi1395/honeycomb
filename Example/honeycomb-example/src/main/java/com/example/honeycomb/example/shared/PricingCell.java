package com.example.honeycomb.example.shared;

import com.example.honeycomb.annotations.Cell;
import com.example.honeycomb.annotations.Sharedwall;
import com.example.honeycomb.example.ExampleConstants;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Cell(port = 9093)
@Component
public class PricingCell implements PricingSharedApi {
    private final String currency = ExampleConstants.Values.USD;

    public Mono<Map<String, Object>> price(Map<String, Object> payload) {
        BigDecimal list = asDecimal(payload.get(ExampleConstants.JsonKeys.LIST_PRICE));
        BigDecimal tax = asDecimal(payload.getOrDefault(ExampleConstants.JsonKeys.TAX_RATE, 0.07));
        BigDecimal total = list.add(list.multiply(tax)).setScale(2, RoundingMode.HALF_UP);
        return Mono.just(Map.of(
                ExampleConstants.JsonKeys.CURRENCY, currency,
                ExampleConstants.JsonKeys.LIST_PRICE, list,
                ExampleConstants.JsonKeys.TAX_RATE, tax,
                ExampleConstants.JsonKeys.TOTAL, total
        ));
    }

    public Mono<Map<String, Object>> discount(Map<String, Object> payload) {
        BigDecimal list = asDecimal(payload.get(ExampleConstants.JsonKeys.LIST_PRICE));
        BigDecimal pct = asDecimal(payload.getOrDefault(ExampleConstants.JsonKeys.DISCOUNT_PCT, 0.10));
        BigDecimal discounted = list.subtract(list.multiply(pct)).setScale(2, RoundingMode.HALF_UP);
        return Mono.just(Map.of(
                ExampleConstants.JsonKeys.CURRENCY, currency,
                ExampleConstants.JsonKeys.LIST_PRICE, list,
                ExampleConstants.JsonKeys.DISCOUNT_PCT, pct,
                ExampleConstants.JsonKeys.DISCOUNTED, discounted
        ));
    }

    public Mono<String> ping() {
        return Mono.just(ExampleConstants.Values.PONG);
    }

    private BigDecimal asDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }
}
