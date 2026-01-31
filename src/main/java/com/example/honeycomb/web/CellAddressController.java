package com.example.honeycomb.web;

import com.example.honeycomb.model.CellAddress;
import com.example.honeycomb.service.CellAddressService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/cells")
public class CellAddressController {
    private final CellAddressService service;

    public CellAddressController(CellAddressService service) { this.service = service; }

    @GetMapping("/addresses")
    public Flux<CellAddress> list() { return service.listAll(); }

    @GetMapping("/addresses/{id}")
    public Mono<ResponseEntity<CellAddress>> get(@PathVariable Long id) {
        return service.get(id).map(ResponseEntity::ok).defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{name}/addresses")
    public Flux<CellAddress> byCell(@PathVariable String name) { return service.findByCell(name); }

    @PostMapping("/addresses")
    public Mono<ResponseEntity<String>> create(@RequestBody CellAddress a) {
        return Mono.just(ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body("Cell address registry is discovery-based and read-only"));
    }

    @PutMapping("/addresses/{id}")
    public Mono<ResponseEntity<String>> update(@PathVariable Long id, @RequestBody CellAddress a) {
        return Mono.just(ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body("Cell address registry is discovery-based and read-only"));
    }

    @DeleteMapping("/addresses/{id}")
    public Mono<ResponseEntity<String>> delete(@PathVariable Long id) {
        return Mono.just(ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body("Cell address registry is discovery-based and read-only"));
    }
}
