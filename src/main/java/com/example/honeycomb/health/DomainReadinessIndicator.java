package com.example.honeycomb.health;

import com.example.honeycomb.service.DomainRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

@Component("domainReadiness")
public class DomainReadinessIndicator implements ReactiveHealthIndicator {
    private final DomainRegistry registry;

    public DomainReadinessIndicator(DomainRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Mono<Health> health() {
        return Mono.fromSupplier(() -> {
            Map<String,Object> details = new LinkedHashMap<>();
            for (String n : registry.getDomainNames()) {
                // readiness placeholder: domain descriptors available
                details.put(n, registry.describeDomain(n));
            }
            return Health.up().withDetails(details).build();
        });
    }
}
