package com.honeycomb.core.service;

import com.honeycomb.core.annotations.Cell;
import com.honeycomb.core.annotations.DependsOnCell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates cell dependencies at application startup.
 *
 * <p>Scans all registered cells for {@link DependsOnCell} annotations
 * and verifies that all declared dependencies exist in the
 * {@link CellRegistry}. Runs after cell registration is complete.</p>
 *
 * @since 1.5.0
 * @see DependsOnCell
 */
@Component
public class CellDependencyValidator {

    private static final Logger log = LoggerFactory.getLogger(CellDependencyValidator.class);

    private final CellRegistry cellRegistry;

    public CellDependencyValidator(CellRegistry cellRegistry) {
        this.cellRegistry = cellRegistry;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)  // Run after CellServerManager starts (default order)
    public void validateDependencies() {
        Set<String> registeredCells = cellRegistry.getCellNames();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (String cellName : registeredCells) {
            cellRegistry.getCellClass(cellName).ifPresent(cls -> {
                DependsOnCell deps = cls.getAnnotation(DependsOnCell.class);
                if (deps == null) return;

                for (String dependency : deps.value()) {
                    if (!registeredCells.contains(dependency)) {
                        String msg = String.format("Cell '%s' depends on '%s' which is not registered",
                                cellName, dependency);
                        if (deps.required()) {
                            errors.add(msg);
                            log.error("DEPENDENCY ERROR: {}", msg);
                        } else {
                            warnings.add(msg);
                            log.warn("DEPENDENCY WARNING: {}", msg);
                        }
                    } else {
                        log.debug("Cell '{}' dependency '{}' — OK", cellName, dependency);
                    }
                }
            });
        }

        if (!warnings.isEmpty()) {
            log.warn("Cell dependency warnings: {}", warnings);
        }

        if (!errors.isEmpty()) {
            String errorMsg = "Cell dependency validation failed:\n  - " + String.join("\n  - ", errors);
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        log.info("Cell dependency validation passed for {} cells", registeredCells.size());
    }
}
