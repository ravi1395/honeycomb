package com.honeycomb.tomcat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Detects the Tomcat servlet context path and, when no explicit
 * {@code honeycomb.cell.name} is configured, sets the cell name from it.
 *
 * <p>When {@code SampleModel.war} is deployed to Tomcat the context path is
 * {@code /SampleModel}. This class strips the leading slash and injects
 * {@code honeycomb.cell.name=SampleModel} into the Spring Environment so that
 * the Honeycomb framework picks it up without any extra configuration.</p>
 *
 * <p>The mapping is logged at INFO level on startup so operators can verify
 * the cell name inferred from the deployment path.</p>
 */
@Component
public class HoneycombContextPathCellNameConfigurer
        implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger log = LoggerFactory.getLogger(HoneycombContextPathCellNameConfigurer.class);

    private static final String PROPERTY_SOURCE_NAME = "honeycombTomcatContextPath";
    private static final String CELL_NAME_PROP = "honeycomb.cell.name";
    /** Context path used by Tomcat for the ROOT webapp — treat as unnamed. */
    private static final String ROOT_CONTEXT = "/";

    private final ConfigurableEnvironment environment;

    @Autowired(required = false)
    private WebApplicationContext webApplicationContext;

    public HoneycombContextPathCellNameConfigurer(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        // Only derive cell name if not already set by the user
        if (environment.containsProperty(CELL_NAME_PROP)) {
            log.info("[Honeycomb-Tomcat] Cell name already configured: {}",
                    environment.getProperty(CELL_NAME_PROP));
            return;
        }

        String contextPath = resolveContextPath(event);
        if (contextPath == null || contextPath.isBlank() || ROOT_CONTEXT.equals(contextPath)) {
            log.warn("[Honeycomb-Tomcat] Could not detect a non-root context path. " +
                     "Deploy your WAR as <CellName>.war or set honeycomb.cell.name explicitly.");
            return;
        }

        // Strip leading slash: "/SampleModel" → "SampleModel"
        String cellName = contextPath.startsWith(ROOT_CONTEXT)
                ? contextPath.substring(1)
                : contextPath;
        // Replace path separators with dots for nested context paths (/a/b → a.b)
        cellName = cellName.replace('/', '.');

        Map<String, Object> props = new LinkedHashMap<>();
        props.put(CELL_NAME_PROP, cellName);

        MutablePropertySources sources = environment.getPropertySources();
        if (sources.contains(PROPERTY_SOURCE_NAME)) {
            sources.replace(PROPERTY_SOURCE_NAME, new MapPropertySource(PROPERTY_SOURCE_NAME, props));
        } else {
            // Lowest priority — explicit config always wins
            sources.addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, props));
        }

        log.info("[Honeycomb-Tomcat] Cell name derived from context path '{}': {}", contextPath, cellName);
    }

    private String resolveContextPath(WebServerInitializedEvent event) {
        try {
            // For servlet-based deployments the web app context holds the context path
            if (webApplicationContext != null) {
                return webApplicationContext.getServletContext() != null
                        ? webApplicationContext.getServletContext().getContextPath()
                        : null;
            }
        } catch (Exception ignored) {
            // Servlet context not available (embedded mode) — fall through
        }
        // Fallback: read spring.webflux.base-path or server.servlet.context-path
        String base = environment.getProperty("server.servlet.context-path",
                environment.getProperty("spring.webflux.base-path", ""));
        return base.isBlank() ? null : base;
    }
}
