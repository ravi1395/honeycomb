package com.example.honeycomb.health;

import com.example.honeycomb.service.DomainRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

@Component("domainLiveness")
public class DomainLivenessIndicator implements ReactiveHealthIndicator {
    private final DomainRegistry registry;

    public DomainLivenessIndicator(DomainRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Mono<Health> health() {
        return Mono.fromSupplier(() -> {
            Map<String,Object> details = new LinkedHashMap<>();
            for (String n : registry.getDomainNames()) {
                // basic liveness check: domain loaded
                details.put(n, "UP");
            }
            return Health.up().withDetails(details).build();
        });
    }
}
