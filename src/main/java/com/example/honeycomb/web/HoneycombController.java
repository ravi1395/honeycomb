package com.example.honeycomb.web;

import com.example.honeycomb.service.DomainRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/honeycomb")
public class HoneycombController {
    private final DomainRegistry registry;

    public HoneycombController(DomainRegistry registry) { this.registry = registry; }

    @GetMapping("/models")
    public Flux<String> listModels() {
        return registry.getDomainNamesFlux();
    }

    @GetMapping("/models/{name}")
    public Mono<ResponseEntity<Map<String,Object>>> describe(@PathVariable String name) {
        return registry.describeDomainMono(name)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
