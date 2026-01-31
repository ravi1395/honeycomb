package com.example.honeycomb.service;

import com.example.honeycomb.annotations.Cell;
import com.example.honeycomb.dto.CellRuntimeStatus;
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
import org.springframework.lang.NonNull;
import reactor.netty.http.server.HttpServer;
import reactor.netty.DisposableServer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PreDestroy;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@SuppressWarnings("null")
public class CellServerManager implements ApplicationContextAware {
    private static final Logger log = LoggerFactory.getLogger(CellServerManager.class);

    private ApplicationContext applicationContext;
    private final CellRegistry cellRegistry;
    private final Environment env;

    private final Map<String, DisposableServer> servers = new ConcurrentHashMap<>();
    private final Map<Integer, String> portToCell = new ConcurrentHashMap<>();

    @Autowired
    public CellServerManager(CellRegistry cellRegistry, Environment env) {
        this.cellRegistry = cellRegistry;
        this.env = env;
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startCellServers() {
        Set<String> names = cellRegistry.getCellNames();
        if (names.isEmpty()) return;
        Flux.fromIterable(names)
                .concatMap(this::startCellServerReactive)
                .subscribe();
    }

    public Mono<Boolean> startCellServerReactive(String name) {
        return Mono.defer(() -> {
            var opt = cellRegistry.getCellClass(name);
            if (opt.isEmpty()) return Mono.just(false);
            Class<?> cls = opt.get();
            Cell ann = cls.getAnnotation(Cell.class);
            int annotatedPort = ann != null ? ann.port() : -1;
            int configuredPort = resolvePort("cell.ports." + name, annotatedPort);
            if (configuredPort <= 0) {
                log.warn("No configured port for cell '{}'", name);
                return Mono.just(false);
            }
            synchronized (this) {
                if (servers.containsKey(name)) {
                    log.info("Server for cell '{}' already running on port {}", name, configuredPort);
                    return Mono.just(true);
                }
            }

            var baseHandler = WebHttpHandlerBuilder.applicationContext(Objects.requireNonNull(applicationContext)).build();
            org.springframework.http.server.reactive.HttpHandler filtered = (req, resp) -> {
                String p = req.getURI().getPath();
                if (p != null && p.startsWith("/honeycomb")) {
                    return baseHandler.handle(req, resp);
                }
                resp.setStatusCode(org.springframework.http.HttpStatus.NOT_FOUND);
                return resp.setComplete();
            };
            var adapter = new ReactorHttpHandlerAdapter(filtered);

            return HttpServer.create().port(configuredPort).handle(adapter).bind()
                    .flatMap(server -> {
                        synchronized (this) {
                            servers.put(name, server);
                            portToCell.put(configuredPort, name);
                        }
                        log.info("Started cell server '{}' on port {}", name, configuredPort);

                        String mgmtKey = "cell.managementPort." + name;
                        int mgmtPort = env.getProperty(mgmtKey, Integer.class, -1);
                        if (mgmtPort <= 0) {
                            return Mono.just(true);
                        }
                        String mgmtBase = env.getProperty("management.endpoints.web.base-path", "/actuator");
                        org.springframework.http.server.reactive.HttpHandler mgmtHandler = (req, resp) -> {
                            String p = req.getURI().getPath();
                            if (p != null && p.startsWith(mgmtBase)) {
                                return baseHandler.handle(req, resp);
                            }
                            resp.setStatusCode(org.springframework.http.HttpStatus.NOT_FOUND);
                            return resp.setComplete();
                        };
                        var mgmtAdapter = new ReactorHttpHandlerAdapter(mgmtHandler);
                        return HttpServer.create().port(mgmtPort).handle(mgmtAdapter).bind()
                                .doOnNext(mgmtServer -> {
                                    synchronized (this) {
                                        servers.put(name + "-mgmt", mgmtServer);
                                        portToCell.put(mgmtPort, name);
                                    }
                                    log.info("Started management server for cell '{}' on port {}", name, mgmtPort);
                                })
                                .thenReturn(true)
                                .onErrorResume(ex -> {
                                    log.warn("Failed to start management server for cell {}: {}", name, ex.getMessage());
                                    return Mono.just(false);
                                });
                    })
                    .onErrorResume(e -> {
                        log.warn("Failed to start server for cell {}: {}", name, e.getMessage());
                        return Mono.just(false);
                    });
        });
    }

    public Mono<Boolean> stopCellServerReactive(String name) {
        return Mono.fromSupplier(() -> {
            boolean stopped = false;
            DisposableServer server = servers.remove(name);
            if (server != null) {
                try {
                    server.dispose();
                    stopped = true;
                } catch (Exception ex) {
                    log.warn("Error shutting down server {}: {}", name, ex.getMessage());
                }
            }
            DisposableServer mgmtServer = servers.remove(name + "-mgmt");
            if (mgmtServer != null) {
                try {
                    mgmtServer.dispose();
                    stopped = true;
                } catch (Exception ex) {
                    log.warn("Error shutting down mgmt server {}: {}", name, ex.getMessage());
                }
            }
            if (stopped) {
                portToCell.entrySet().removeIf(e -> e.getValue().equals(name));
            }
            return stopped;
        });
    }

    public Mono<Boolean> restartCellServerReactive(String name) {
        return stopCellServerReactive(name)
                .flatMap(stopped -> startCellServerReactive(name)
                        .map(started -> stopped || started));
    }

    public synchronized Optional<CellRuntimeStatus> getCellStatus(String name) {
        return cellRegistry.getCellClass(name).map(cls -> {
            Cell ann = cls.getAnnotation(Cell.class);
            int annotatedPort = ann != null ? ann.port() : -1;
            int configuredPort = resolvePort("cell.ports." + name, annotatedPort);
            int mgmtPort = env.getProperty("cell.managementPort." + name, Integer.class, -1);
            DisposableServer server = servers.get(name);
            DisposableServer mgmtServer = servers.get(name + "-mgmt");
            Integer runningPort = server != null ? server.port() : null;
            Integer runningMgmtPort = mgmtServer != null ? mgmtServer.port() : null;
            return new CellRuntimeStatus(
                    name,
                    configuredPort > 0 ? configuredPort : null,
                    runningPort,
                    mgmtPort > 0 ? mgmtPort : null,
                    server != null && !server.isDisposed(),
                    mgmtServer != null && !mgmtServer.isDisposed(),
                    runningMgmtPort
            );
        });
    }

    public synchronized List<CellRuntimeStatus> listCellStatuses() {
        List<CellRuntimeStatus> out = new ArrayList<>();
        for (String name : cellRegistry.getCellNames()) {
            getCellStatus(name).ifPresent(out::add);
        }
        return out;
    }

    public synchronized boolean startCellServer(String name) {
        try {
            var opt = cellRegistry.getCellClass(name);
            if (opt.isEmpty()) return false;
            Class<?> cls = opt.get();
            Cell ann = cls.getAnnotation(Cell.class);
            int annotatedPort = ann != null ? ann.port() : -1;
            int configuredPort = resolvePort("cell.ports." + name, annotatedPort);
            if (configuredPort <= 0) {
                log.warn("No configured port for cell '{}'", name);
                return false;
            }
            if (servers.containsKey(name)) {
                log.info("Server for cell '{}' already running on port {}", name, configuredPort);
                return true;
            }

            var baseHandler = WebHttpHandlerBuilder.applicationContext(Objects.requireNonNull(applicationContext)).build();
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

            String mgmtKey = "cell.managementPort." + name;
            int mgmtPort = env.getProperty(mgmtKey, Integer.class, -1);
            if (mgmtPort > 0) {
                String mgmtBase = env.getProperty("management.endpoints.web.base-path", "/actuator");
                org.springframework.http.server.reactive.HttpHandler mgmtHandler = (req, resp) -> {
                    String p = req.getURI().getPath();
                    if (p != null && p.startsWith(mgmtBase)) {
                        return baseHandler.handle(req, resp);
                    }
                    resp.setStatusCode(org.springframework.http.HttpStatus.NOT_FOUND);
                    return resp.setComplete();
                };
                var mgmtAdapter = new ReactorHttpHandlerAdapter(mgmtHandler);
                DisposableServer mgmtServer = HttpServer.create().port(mgmtPort).handle(mgmtAdapter).bindNow();
                servers.put(name + "-mgmt", mgmtServer);
                portToCell.put(mgmtPort, name);
                log.info("Started management server for cell '{}' on port {}", name, mgmtPort);
            }

            portToCell.put(configuredPort, name);
            log.info("Started cell server '{}' on port {}", name, configuredPort);
            return true;
        } catch (Exception e) {
            log.warn("Failed to start server for cell {}: {}", name, e.getMessage());
            return false;
        }
    }

    public synchronized boolean stopCellServer(String name) {
        boolean stopped = false;
        DisposableServer server = servers.remove(name);
        if (server != null) {
            try {
                server.disposeNow();
                stopped = true;
            } catch (Exception ex) {
                log.warn("Error shutting down server {}: {}", name, ex.getMessage());
            }
        }
        DisposableServer mgmtServer = servers.remove(name + "-mgmt");
        if (mgmtServer != null) {
            try {
                mgmtServer.disposeNow();
                stopped = true;
            } catch (Exception ex) {
                log.warn("Error shutting down mgmt server {}: {}", name, ex.getMessage());
            }
        }
        if (stopped) {
            portToCell.entrySet().removeIf(e -> e.getValue().equals(name));
        }
        return stopped;
    }

    public synchronized boolean restartCellServer(String name) {
        boolean wasRunning = stopCellServer(name);
        boolean started = startCellServer(name);
        return wasRunning || started;
    }

    @PreDestroy
    public void shutdown() {
        for (Map.Entry<String, DisposableServer> e : servers.entrySet()) {
            try {
                log.info("Shutting down cell server '{}'", e.getKey());
                e.getValue().disposeNow();
            } catch (Exception ex) {
                log.warn("Error shutting down server {}: {}", e.getKey(), ex.getMessage());
            }
        }
        servers.clear();
    }

    /**
     * Returns the cell name (if any) that is served on the given local port.
     */
    public String getCellForPort(int port) {
        return portToCell.get(port);
    }

    private int resolvePort(String propKey, int fallback) {
        String raw = Objects.requireNonNullElse(env.getProperty(propKey), "");
        if (raw == null || raw.isBlank()) return fallback;
        String first = raw.split(",")[0].trim();
        try {
            int v = Integer.parseInt(first);
            return v > 0 ? v : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
