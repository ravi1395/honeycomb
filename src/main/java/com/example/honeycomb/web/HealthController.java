package com.example.honeycomb.web;

import com.example.honeycomb.service.DomainRegistry;
import com.example.honeycomb.service.DomainServerManager;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/honeycomb")
public class HealthController {
    private final DomainRegistry registry;
    private final DomainServerManager serverManager;

    public HealthController(DomainRegistry registry, DomainServerManager serverManager) {
        this.registry = registry;
        this.serverManager = serverManager;
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String,Object>>> health(ServerHttpRequest request) {
        Integer port = request.getLocalAddress() == null ? null : request.getLocalAddress().getPort();
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("status", "UP");
        m.put("port", port);
        if (port != null) {
            String domain = serverManager.getDomainForPort(port);
            m.put("domain", domain);
            if (domain != null) {
                m.putAll(registry.describeDomain(domain));
            }
        }
        return Mono.just(ResponseEntity.ok(m));
    }
}
