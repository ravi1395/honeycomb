package com.example.honeycomb.web;

import com.example.honeycomb.service.DomainRegistry;
import com.example.honeycomb.service.DomainDataStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;


@RestController
@RequestMapping("/honeycomb")
public class HoneycombController {
    private final DomainRegistry registry;
    private final DomainDataStore dataStore;

    public HoneycombController(DomainRegistry registry, DomainDataStore dataStore) { this.registry = registry; this.dataStore = dataStore; }

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

    // --- CRUD for domain instances -------------------------------------------------
    @GetMapping("/models/{name}/items")
    public Flux<Map<String,Object>> listItems(@PathVariable String name) {
        return dataStore.list(name);
    }

    @GetMapping("/models/{name}/items/{id}")
    public Mono<ResponseEntity<Map<String,Object>>> getItem(@PathVariable String name, @PathVariable String id) {
        return dataStore.get(name, id).map(ResponseEntity::ok).defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/models/{name}/items")
    public Mono<ResponseEntity<Map<String,Object>>> createItem(@PathVariable String name, @RequestBody Map<String,Object> body) {
        return dataStore.create(name, body).map(b -> ResponseEntity.status(201).body(b));
    }

    @PutMapping("/models/{name}/items/{id}")
    public Mono<ResponseEntity<Map<String,Object>>> updateItem(@PathVariable String name, @PathVariable String id, @RequestBody Map<String,Object> body) {
        return dataStore.update(name, id, body).map(ResponseEntity::ok).defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/models/{name}/items/{id}")
    public Mono<ResponseEntity<Void>> deleteItem(@PathVariable String name, @PathVariable String id) {
        return dataStore.delete(name, id).map(ok -> ok ? ResponseEntity.noContent().<Void>build() : ResponseEntity.notFound().build());
    }

}
