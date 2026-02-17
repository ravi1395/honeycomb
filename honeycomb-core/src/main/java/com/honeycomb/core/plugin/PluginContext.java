package com.honeycomb.core.plugin;

import com.honeycomb.core.service.CellRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

/**
 * Context object provided to {@link HoneycombPlugin} instances during initialization.
 *
 * <p>Gives plugins read-only access to the Honeycomb framework's services,
 * configuration, and Spring application context.</p>
 *
 * @since 1.5.0
 */
public class PluginContext {

    private final ApplicationContext applicationContext;
    private final CellRegistry cellRegistry;
    private final Environment environment;

    public PluginContext(ApplicationContext applicationContext,
                        CellRegistry cellRegistry,
                        Environment environment) {
        this.applicationContext = applicationContext;
        this.cellRegistry = cellRegistry;
        this.environment = environment;
    }

    /**
     * Access the Spring application context for bean lookups.
     */
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * Access the cell registry for querying registered cells.
     */
    public CellRegistry getCellRegistry() {
        return cellRegistry;
    }

    /**
     * Access the environment for configuration properties.
     */
    public Environment getEnvironment() {
        return environment;
    }

    /**
     * Look up a bean by type from the application context.
     */
    public <T> T getBean(Class<T> beanClass) {
        return applicationContext.getBean(beanClass);
    }

    /**
     * Read a configuration property.
     */
    public String getProperty(String key) {
        return environment.getProperty(key);
    }

    /**
     * Read a configuration property with a default value.
     */
    public String getProperty(String key, String defaultValue) {
        return environment.getProperty(key, defaultValue);
    }
}
