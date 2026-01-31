package com.example.honeycomb.example.shared;

import com.example.honeycomb.annotations.Cell;
import com.example.honeycomb.annotations.Sharedwall;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Cell(port = 9093)
@Component
public class PricingCell {
    private final String currency = "USD";

    @Sharedwall("price")
    public Mono<Map<String, Object>> price(Map<String, Object> payload) {
        BigDecimal list = asDecimal(payload.get("listPrice"));
        BigDecimal tax = asDecimal(payload.getOrDefault("taxRate", 0.07));
        BigDecimal total = list.add(list.multiply(tax)).setScale(2, RoundingMode.HALF_UP);
        return Mono.just(Map.of(
                "currency", currency,
                "listPrice", list,
                "taxRate", tax,
                "total", total
        ));
    }

    @Sharedwall(value = "discount", allowedFrom = {"demo-client"})
    public Mono<Map<String, Object>> discount(Map<String, Object> payload) {
        BigDecimal list = asDecimal(payload.get("listPrice"));
        BigDecimal pct = asDecimal(payload.getOrDefault("discountPct", 0.10));
        BigDecimal discounted = list.subtract(list.multiply(pct)).setScale(2, RoundingMode.HALF_UP);
        return Mono.just(Map.of(
                "currency", currency,
                "listPrice", list,
                "discountPct", pct,
                "discounted", discounted
        ));
    }

    @Sharedwall("ping")
    public Mono<String> ping() {
        return Mono.just("pong");
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
