package com.example.honeycomb.web;

import com.example.honeycomb.service.CellSwaggerService;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/honeycomb/swagger")
public class CellSwaggerController {
    private final CellSwaggerService swaggerService;

    public CellSwaggerController(CellSwaggerService swaggerService) {
        this.swaggerService = swaggerService;
    }

    @GetMapping({"", "/"})
    public Mono<OpenAPI> getAllCellsSwagger() {
        return Mono.just(swaggerService.buildForAllCells());
    }

    @GetMapping("/cells/{name}")
    public Mono<ResponseEntity<OpenAPI>> getCellSwagger(@PathVariable String name) {
        return Mono.just(swaggerService.buildForCell(name)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build()));
    }
}