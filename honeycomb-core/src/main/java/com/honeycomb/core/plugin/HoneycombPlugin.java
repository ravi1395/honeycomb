package com.honeycomb.core.plugin;

import java.util.Map;

/**
 * Service Provider Interface (SPI) for Honeycomb plugins.
 *
 * <p>Plugins can hook into the Honeycomb lifecycle to:
 * <ul>
 *   <li>Initialize resources at startup ({@link #onStartup(PluginContext)})</li>
 *   <li>Intercept cell operations ({@link #onBeforeOperation(String, String, Map)} /
 *       {@link #onAfterOperation(String, String, Map, Object)})</li>
 *   <li>React to cell registration ({@link #onCellRegistered(String, Class)})</li>
 *   <li>Clean up on shutdown ({@link #onShutdown()})</li>
 * </ul>
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader}
 * using {@code META-INF/services/com.honeycomb.core.plugin.HoneycombPlugin}
 * or by registering as Spring beans.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * public class MetricsPlugin implements HoneycombPlugin {
 *     @Override
 *     public String getName() { return "custom-metrics"; }
 *
 *     @Override
 *     public void onStartup(PluginContext ctx) {
 *         // register custom metrics
 *     }
 *
 *     @Override
 *     public void onAfterOperation(String cell, String op, Map<String, Object> meta, Object result) {
 *         // record operation metrics
 *     }
 * }
 * }</pre>
 *
 * @since 1.5.0
 * @see PluginContext
 * @see PluginManager
 */
public interface HoneycombPlugin {

    /**
     * Unique name for this plugin (used in logs and configuration).
     */
    String getName();

    /**
     * Plugin version string.
     */
    default String getVersion() {
        return "1.0.0";
    }

    /**
     * Priority for plugin execution order. Lower values execute first.
     * Default is 100.
     */
    default int getOrder() {
        return 100;
    }

    /**
     * Called when the Honeycomb framework starts up.
     *
     * @param context provides access to the framework's services and configuration
     */
    default void onStartup(PluginContext context) {}

    /**
     * Called when a new cell is registered in the cell registry.
     *
     * @param cellName the name of the registered cell
     * @param cellClass the class of the registered cell
     */
    default void onCellRegistered(String cellName, Class<?> cellClass) {}

    /**
     * Called before a cell operation (CRUD or shared method invocation).
     * Can modify the metadata map to influence operation behavior.
     *
     * @param cellName the target cell name
     * @param operation the operation type (create, read, update, delete, shared)
     * @param metadata mutable map of operation metadata
     * @return {@code true} to proceed, {@code false} to abort the operation
     */
    default boolean onBeforeOperation(String cellName, String operation, Map<String, Object> metadata) {
        return true;
    }

    /**
     * Called after a cell operation completes successfully.
     *
     * @param cellName the target cell name
     * @param operation the operation type
     * @param metadata operation metadata
     * @param result the operation result (may be null)
     */
    default void onAfterOperation(String cellName, String operation, Map<String, Object> metadata, Object result) {}

    /**
     * Called when a cell operation fails with an exception.
     *
     * @param cellName the target cell name
     * @param operation the operation type
     * @param metadata operation metadata
     * @param error the exception that occurred
     */
    default void onOperationError(String cellName, String operation, Map<String, Object> metadata, Throwable error) {}

    /**
     * Called during Honeycomb framework shutdown.
     */
    default void onShutdown() {}
}
