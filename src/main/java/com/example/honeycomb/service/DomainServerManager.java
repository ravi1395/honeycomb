package com.example.honeycomb.service;

import com.example.honeycomb.annotations.Domain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import reactor.netty.http.server.HttpServer;
import reactor.netty.DisposableServer;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DomainServerManager implements ApplicationContextAware {
    private static final Logger log = LoggerFactory.getLogger(DomainServerManager.class);

    private ApplicationContext applicationContext;
    private final DomainRegistry domainRegistry;
    private final Environment env;

    private final Map<String, DisposableServer> servers = new ConcurrentHashMap<>();
    private final Map<Integer, String> portToDomain = new ConcurrentHashMap<>();

    @Autowired
    public DomainServerManager(DomainRegistry domainRegistry, Environment env) {
        this.domainRegistry = domainRegistry;
        this.env = env;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startDomainServers() {
        Set<String> names = domainRegistry.getDomainNames();
        if (names.isEmpty()) return;

        // create a single HttpHandler from the existing application context and reuse it
        var baseHandler = WebHttpHandlerBuilder.applicationContext(applicationContext).build();

        for (String name : names) {
            try {
                var opt = domainRegistry.getDomainClass(name);
                if (opt.isEmpty()) continue;
                Class<?> cls = opt.get();
                Domain ann = cls.getAnnotation(Domain.class);
                int annotatedPort = ann != null ? ann.port() : -1;
                String propKey = "domain.ports." + name;
                int configuredPort = env.getProperty(propKey, Integer.class, annotatedPort);
                if (configuredPort > 0) {
                    if (servers.containsKey(name)) {
                        log.info("Server for domain '{}' already running on port {}", name, configuredPort);
                        continue;
                    }
                    // wrap the base handler to restrict routes served by per-domain servers
                    org.springframework.http.server.reactive.HttpHandler filtered = (req, resp) -> {
                        String p = req.getURI().getPath();
                        if (p != null && p.startsWith("/honeycomb")) {
                            return baseHandler.handle(req, resp);
                        }
                        resp.setStatusCode(org.springframework.http.HttpStatus.NOT_FOUND);
                        return resp.setComplete();
                    };
                    var adapter = new ReactorHttpHandlerAdapter(filtered);
                    DisposableServer server = HttpServer.create().port(configuredPort).handle(adapter).bindNow();
                    servers.put(name, server);
                    // record reverse mapping port -> domain name
                    portToDomain.put(configuredPort, name);
                    log.info("Started domain server '{}' on port {}", name, configuredPort);
                }
            } catch (Exception e) {
                log.warn("Failed to start server for domain {}: {}", name, e.getMessage());
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        for (Map.Entry<String, DisposableServer> e : servers.entrySet()) {
            try {
                log.info("Shutting down domain server '{}'", e.getKey());
                e.getValue().disposeNow();
            } catch (Exception ex) {
                log.warn("Error shutting down server {}: {}", e.getKey(), ex.getMessage());
            }
        }
        servers.clear();
    }

    /**
     * Returns the domain name (if any) that is served on the given local port.
     */
    public String getDomainForPort(int port) {
        return portToDomain.get(port);
    }
}
