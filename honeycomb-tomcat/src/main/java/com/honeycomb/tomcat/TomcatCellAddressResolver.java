package com.honeycomb.tomcat;

import com.honeycomb.core.model.CellAddress;
import com.honeycomb.core.service.CellRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cell address resolver for Tomcat WAR deployments.
 *
 * <p>In a single-Tomcat deployment, all cell WARs share the same
 * {@code host:port} but each gets a unique context path derived from the
 * WAR filename.  This component maintains a map of
 * {@code cellName → CellAddress(host, port, contextPath)} entries so that
 * {@link com.honeycomb.core.web.CellInteractionController} and
 * {@link com.honeycomb.core.client.SharedwallClient} can route to the
 * correct context path.</p>
 *
 * <h2>Activation</h2>
 * Set {@code honeycomb.tomcat.discovery.enabled=true} in your
 * {@code application.yml} (automatically set by
 * {@link HoneycombTomcatAutoConfiguration}).
 *
 * <h2>Configuration</h2>
 * <pre>
 * honeycomb:
 *   tomcat:
 *     discovery:
 *       enabled: true
 *       host: localhost            # Tomcat bind address
 *       port: 8080                 # Tomcat HTTP port
 *       cells:                     # explicit cell→contextPath mapping (optional)
 *         SampleModel: /SampleModel
 *         InventoryCell: /InventoryCell
 * </pre>
 *
 * <p>If {@code cells} is not configured, the resolver assumes every cell
 * registered in the {@link CellRegistry} is deployed at
 * {@code /<cellName>}.</p>
 *
 * @see CellAddress#getBaseUrl()
 * @see HoneycombContextPathCellNameConfigurer
 */
@Component
@ConditionalOnProperty(name = "honeycomb.tomcat.discovery.enabled", havingValue = "true", matchIfMissing = false)
public class TomcatCellAddressResolver {

    private static final Logger log = LoggerFactory.getLogger(TomcatCellAddressResolver.class);

    private final CellRegistry cellRegistry;
    private final Environment env;

    @Value("${honeycomb.tomcat.discovery.host:localhost}")
    private String tomcatHost;

    @Value("${honeycomb.tomcat.discovery.port:8080}")
    private int tomcatPort;

    /** Resolved addresses: cellName → CellAddress. */
    private final Map<String, CellAddress> addressMap = new ConcurrentHashMap<>();

    public TomcatCellAddressResolver(CellRegistry cellRegistry, Environment env) {
        this.cellRegistry = cellRegistry;
        this.env = env;
    }

    @PostConstruct
    public void init() {
        Set<String> cellNames = cellRegistry.getCellNames();

        for (String name : cellNames) {
            // Check for explicit context-path config: honeycomb.tomcat.discovery.cells.<name>
            String explicitCtx = env.getProperty("honeycomb.tomcat.discovery.cells." + name);
            String contextPath = (explicitCtx != null && !explicitCtx.isBlank())
                    ? explicitCtx
                    : "/" + name;

            CellAddress addr = new CellAddress(null, name, tomcatHost, tomcatPort, contextPath);
            addressMap.put(name, addr);
            log.info("[Honeycomb-Tomcat] Registered cell '{}' at {}", name, addr.getBaseUrl());
        }

        if (addressMap.isEmpty()) {
            log.warn("[Honeycomb-Tomcat] No cells found in CellRegistry. " +
                     "Ensure @Cell-annotated classes are on the classpath.");
        }
    }

    /**
     * Resolve addresses for a cell by name. Returns a single-element Flux
     * since all cells share the same Tomcat — the only difference is the
     * context path.
     */
    public Flux<CellAddress> findByCell(String cellName) {
        CellAddress addr = addressMap.get(cellName);
        if (addr != null) {
            return Flux.just(addr);
        }
        // Fallback: assume the cell is deployed at /<cellName> on this Tomcat
        return Flux.just(new CellAddress(null, cellName, tomcatHost, tomcatPort, "/" + cellName));
    }

    /**
     * List all registered Tomcat-deployed cell addresses.
     */
    public Flux<CellAddress> listAll() {
        return Flux.fromIterable(addressMap.values());
    }

    /** Exposed for testing. */
    Map<String, CellAddress> getAddressMap() {
        return Map.copyOf(addressMap);
    }
}
