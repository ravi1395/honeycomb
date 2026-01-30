package com.example.honeycomb.health;

import com.example.honeycomb.service.DomainRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DomainReactiveHealthIndicator implements ReactiveHealthIndicator {
    private final DomainRegistry registry;

    public DomainReactiveHealthIndicator(DomainRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Mono<Health> health() {
        return Mono.fromSupplier(() -> {
            var names = registry.getDomainNames();
            Map<String,Object> domains = new LinkedHashMap<>();
            for (String n : names) {
                domains.put(n, registry.describeDomain(n));
            }
            return Health.up().withDetail("domainCount", names.size()).withDetail("domains", domains).build();
        });
    }
}
