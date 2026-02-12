package com.example.honeycomb.health;

import com.example.honeycomb.service.CellRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

@Component("cellReadiness")
public class CellReadinessIndicator implements ReactiveHealthIndicator {
    private final CellRegistry registry;

    public CellReadinessIndicator(CellRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Mono<Health> health() {
        return Mono.fromSupplier(() -> {
            Map<String,Object> details = new LinkedHashMap<>();
            for (String n : registry.getCellNames()) {
                // readiness placeholder: cell descriptors available
                details.put(n, registry.describeCell(n));
            }
            return Health.up().withDetails(details).build();
        });
    }
}
