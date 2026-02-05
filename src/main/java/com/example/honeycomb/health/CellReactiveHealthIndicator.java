package com.example.honeycomb.health;

import com.example.honeycomb.service.CellRegistry;
import com.example.honeycomb.util.HoneycombConstants;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

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
