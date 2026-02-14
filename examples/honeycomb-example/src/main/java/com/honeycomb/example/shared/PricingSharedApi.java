package com.honeycomb.example.shared;

import com.honeycomb.core.annotations.Sharedwall;
import com.honeycomb.example.ExampleConstants;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Typed interface for the Pricing cell’s shared methods.
 *
 * <p>Annotated with {@link Sharedwall} at type level so all methods
 * are exposed. Individual methods can override with their own
 * {@code @Sharedwall} for versioning and access control.</p>
 */
@Sharedwall
public interface PricingSharedApi {
    Mono<Map<String, Object>> price(Map<String, Object> payload);

    @Sharedwall(value = ExampleConstants.Shared.DISCOUNT, allowedFrom = {ExampleConstants.Shared.DEMO_CALLER})
    Mono<Map<String, Object>> discount(Map<String, Object> payload);

    @Sharedwall(ExampleConstants.Shared.PING)
    Mono<String> ping();
}
