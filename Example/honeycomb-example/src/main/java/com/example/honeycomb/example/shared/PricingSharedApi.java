package com.example.honeycomb.example.shared;

import com.example.honeycomb.annotations.Sharedwall;
import com.example.honeycomb.example.ExampleConstants;
import reactor.core.publisher.Mono;

import java.util.Map;

@Sharedwall
public interface PricingSharedApi {
    Mono<Map<String, Object>> price(Map<String, Object> payload);

    @Sharedwall(value = ExampleConstants.Shared.DISCOUNT, allowedFrom = {ExampleConstants.Shared.DEMO_CALLER})
    Mono<Map<String, Object>> discount(Map<String, Object> payload);

    @Sharedwall(ExampleConstants.Shared.PING)
    Mono<String> ping();
}
