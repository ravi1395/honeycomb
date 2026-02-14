package com.honeycomb.core.health;

import com.honeycomb.core.service.CellRegistry;
import com.honeycomb.core.util.HoneycombConstants;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Boot Actuator health indicator that reports the overall status of
 * registered Honeycomb cells.
 *
 * <p>Exposed at {@code /actuator/health} (or the configured management path).
 * Includes the total cell count and per-cell descriptor maps as health details.</p>
 *
 * @see CellRegistry
 */
@Component
public class CellReactiveHealthIndicator implements ReactiveHealthIndicator {
    private final CellRegistry registry;

    public CellReactiveHealthIndicator(CellRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Mono<Health> health() {
        return Mono.fromSupplier(() -> {
            var names = registry.getCellNames();
            Map<String,Object> cells = new LinkedHashMap<>();
            for (String n : names) {
                cells.put(n, registry.describeCell(n));
            }
            return Health.up()
                    .withDetail(HoneycombConstants.Health.DETAIL_CELL_COUNT, names.size())
                    .withDetail(HoneycombConstants.Health.DETAIL_CELLS, cells)
                    .build();
        });
    }
}
