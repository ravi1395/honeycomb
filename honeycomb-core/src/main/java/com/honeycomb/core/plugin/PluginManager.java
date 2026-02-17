package com.honeycomb.core.plugin;

import com.honeycomb.core.service.CellRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the lifecycle of {@link HoneycombPlugin} instances.
 *
 * <p>Discovers plugins via:
 * <ol>
 *   <li>Java {@link ServiceLoader} SPI ({@code META-INF/services/com.honeycomb.core.plugin.HoneycombPlugin})</li>
 *   <li>Spring beans implementing {@link HoneycombPlugin}</li>
 * </ol>
 *
 * <p>Plugins are sorted by {@link HoneycombPlugin#getOrder()} (ascending)
 * and their lifecycle methods are invoked in order.</p>
 *
 * @since 1.5.0
 * @see HoneycombPlugin
 */
@Component
public class PluginManager {

    private static final Logger log = LoggerFactory.getLogger(PluginManager.class);

    private final ApplicationContext applicationContext;
    private final CellRegistry cellRegistry;
    private final Environment environment;
    private final List<HoneycombPlugin> plugins = new CopyOnWriteArrayList<>();

    public PluginManager(ApplicationContext applicationContext,
                         CellRegistry cellRegistry,
                         Environment environment) {
        this.applicationContext = applicationContext;
        this.cellRegistry = cellRegistry;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(20)  // After cell dependency validation
    public void initializePlugins() {
        // Discover plugins via ServiceLoader
        ServiceLoader<HoneycombPlugin> serviceLoader = ServiceLoader.load(HoneycombPlugin.class);
        for (HoneycombPlugin plugin : serviceLoader) {
            plugins.add(plugin);
            log.info("Discovered SPI plugin: {} v{}", plugin.getName(), plugin.getVersion());
        }

        // Discover plugins registered as Spring beans
        Map<String, HoneycombPlugin> beanPlugins = applicationContext.getBeansOfType(HoneycombPlugin.class);
        for (HoneycombPlugin plugin : beanPlugins.values()) {
            if (!plugins.contains(plugin)) {
                plugins.add(plugin);
                log.info("Discovered Spring bean plugin: {} v{}", plugin.getName(), plugin.getVersion());
            }
        }

        // Sort by order
        plugins.sort(Comparator.comparingInt(HoneycombPlugin::getOrder));

        // Initialize all plugins
        PluginContext ctx = new PluginContext(applicationContext, cellRegistry, environment);
        for (HoneycombPlugin plugin : plugins) {
            try {
                plugin.onStartup(ctx);
                log.info("Initialized plugin: {} v{}", plugin.getName(), plugin.getVersion());
            } catch (Exception ex) {
                log.error("Failed to initialize plugin '{}': {}", plugin.getName(), ex.getMessage(), ex);
            }
        }

        log.info("Honeycomb plugin system initialized with {} plugins", plugins.size());
    }

    /**
     * Notify all plugins before a cell operation.
     *
     * @return {@code true} if all plugins allow the operation to proceed
     */
    public boolean fireBeforeOperation(String cellName, String operation, Map<String, Object> metadata) {
        for (HoneycombPlugin plugin : plugins) {
            try {
                if (!plugin.onBeforeOperation(cellName, operation, metadata)) {
                    log.debug("Plugin '{}' blocked operation {} on cell {}", plugin.getName(), operation, cellName);
                    return false;
                }
            } catch (Exception ex) {
                log.warn("Plugin '{}' error in onBeforeOperation: {}", plugin.getName(), ex.getMessage());
            }
        }
        return true;
    }

    /**
     * Notify all plugins after a successful cell operation.
     */
    public void fireAfterOperation(String cellName, String operation, Map<String, Object> metadata, Object result) {
        for (HoneycombPlugin plugin : plugins) {
            try {
                plugin.onAfterOperation(cellName, operation, metadata, result);
            } catch (Exception ex) {
                log.warn("Plugin '{}' error in onAfterOperation: {}", plugin.getName(), ex.getMessage());
            }
        }
    }

    /**
     * Notify all plugins when a cell operation fails.
     */
    public void fireOperationError(String cellName, String operation, Map<String, Object> metadata, Throwable error) {
        for (HoneycombPlugin plugin : plugins) {
            try {
                plugin.onOperationError(cellName, operation, metadata, error);
            } catch (Exception ex) {
                log.warn("Plugin '{}' error in onOperationError: {}", plugin.getName(), ex.getMessage());
            }
        }
    }

    /**
     * Notify all plugins when a cell is registered.
     */
    public void fireCellRegistered(String cellName, Class<?> cellClass) {
        for (HoneycombPlugin plugin : plugins) {
            try {
                plugin.onCellRegistered(cellName, cellClass);
            } catch (Exception ex) {
                log.warn("Plugin '{}' error in onCellRegistered: {}", plugin.getName(), ex.getMessage());
            }
        }
    }

    @PreDestroy
    public void shutdownPlugins() {
        for (HoneycombPlugin plugin : plugins) {
            try {
                plugin.onShutdown();
                log.info("Shut down plugin: {}", plugin.getName());
            } catch (Exception ex) {
                log.warn("Error shutting down plugin '{}': {}", plugin.getName(), ex.getMessage());
            }
        }
    }

    /**
     * Returns an unmodifiable list of registered plugins.
     */
    public List<HoneycombPlugin> getPlugins() {
        return Collections.unmodifiableList(plugins);
    }
}
