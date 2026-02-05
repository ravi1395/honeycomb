package com.example.honeycomb.web;

import com.example.honeycomb.model.CellAddress;
import com.example.honeycomb.service.CellAddressService;
import com.example.honeycomb.util.HoneycombConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(HoneycombConstants.Paths.CELLS_BASE)
public class CellAddressController {
    private final CellAddressService service;

    public CellAddressController(CellAddressService service) { this.service = service; }

    @GetMapping(HoneycombConstants.Paths.ADDRESSES_PATH)
    public Flux<CellAddress> list() { return service.listAll(); }

    @GetMapping(HoneycombConstants.Paths.ADDRESSES_PATH + HoneycombConstants.Paths.ID_PATH)
    public Mono<ResponseEntity<CellAddress>> get(@PathVariable Long id) {
        return service.get(id).map(ResponseEntity::ok).defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{name}/" + HoneycombConstants.Paths.ADDRESSES)
    public Flux<CellAddress> byCell(@PathVariable String name) { return service.findByCell(name); }

    @PostMapping(HoneycombConstants.Paths.ADDRESSES_PATH)
    public Mono<ResponseEntity<String>> create(@RequestBody CellAddress a) {
        return Mono.just(ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(HoneycombConstants.Messages.CELL_ADDRESS_READ_ONLY));
    }

    @PutMapping(HoneycombConstants.Paths.ADDRESSES_PATH + HoneycombConstants.Paths.ID_PATH)
    public Mono<ResponseEntity<String>> update(@PathVariable Long id, @RequestBody CellAddress a) {
        return Mono.just(ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(HoneycombConstants.Messages.CELL_ADDRESS_READ_ONLY));
    }

    @DeleteMapping(HoneycombConstants.Paths.ADDRESSES_PATH + HoneycombConstants.Paths.ID_PATH)
    public Mono<ResponseEntity<String>> delete(@PathVariable Long id) {
        return Mono.just(ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(HoneycombConstants.Messages.CELL_ADDRESS_READ_ONLY));
    }
}
