package com.honeycomb.core.health;

import com.honeycomb.core.service.CellRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kubernetes-style readiness probe for Honeycomb cells.
 *
 * <p>Registered as the {@code cellReadiness} health component.
 * Reports {@code UP} when every cell's descriptor is resolvable,
 * indicating the registry is initialised and ready to serve traffic.</p>
 *
 * @see CellRegistry
 */
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
