package com.honeycomb.core.health;

import com.honeycomb.core.service.CellRegistry;
import com.honeycomb.core.util.HoneycombConstants;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

@Component(HoneycombConstants.Health.LIVENESS_COMPONENT)
public class CellLivenessIndicator implements ReactiveHealthIndicator {
    private final CellRegistry registry;

    public CellLivenessIndicator(CellRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Mono<Health> health() {
        return Mono.fromSupplier(() -> {
            Map<String,Object> details = new LinkedHashMap<>();
            for (String n : registry.getCellNames()) {
                // basic liveness check: cell loaded
                details.put(n, HoneycombConstants.Health.STATUS_UP);
            }
            return Health.up().withDetails(details).build();
        });
    }
}
