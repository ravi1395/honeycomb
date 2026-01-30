package com.example.honeycomb.web;

import com.example.honeycomb.model.DomainAddress;
import com.example.honeycomb.service.DomainAddressService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/domains")
public class DomainAddressController {
    private final DomainAddressService service;

    public DomainAddressController(DomainAddressService service) { this.service = service; }

    @GetMapping("/addresses")
    public Flux<DomainAddress> list() { return service.listAll(); }

    @GetMapping("/addresses/{id}")
    public Mono<ResponseEntity<DomainAddress>> get(@PathVariable Long id) {
        return service.get(id).map(ResponseEntity::ok).defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{name}/addresses")
    public Flux<DomainAddress> byDomain(@PathVariable String name) { return service.findByDomain(name); }

    @PostMapping("/addresses")
    public Mono<ResponseEntity<DomainAddress>> create(@RequestBody DomainAddress a) {
        return service.create(a).map(b -> ResponseEntity.status(201).body(b));
    }

    @PutMapping("/addresses/{id}")
    public Mono<ResponseEntity<DomainAddress>> update(@PathVariable Long id, @RequestBody DomainAddress a) {
        return service.update(id, a).map(ResponseEntity::ok).defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/addresses/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable Long id) {
        return service.delete(id).then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }
}
