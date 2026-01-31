package com.example.honeycomb.web;

import com.example.honeycomb.dto.ErrorCode;
import com.example.honeycomb.dto.ErrorResponse;
import com.example.honeycomb.service.DomainRegistry;
import com.example.honeycomb.service.DomainDataStore;
import com.example.honeycomb.config.HoneycombProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;


@RestController
@RequestMapping("/honeycomb")
@Tag(name = "Domain Registry", description = "CRUD operations for domain models and instances")
@Validated
public class HoneycombController {
    private static final Logger log = LoggerFactory.getLogger(HoneycombController.class);
    private final DomainRegistry registry;
    private final DomainDataStore dataStore;
    private final HoneycombProperties props;

    public HoneycombController(DomainRegistry registry, DomainDataStore dataStore, HoneycombProperties props) { this.registry = registry; this.dataStore = dataStore; this.props = props; }

    @Operation(summary = "List all registered domain models")
    @ApiResponse(responseCode = "200", description = "List of domain names")
    @GetMapping("/models")
    public Flux<String> listModels() {
        return registry.getDomainNamesFlux();
    }

    @Operation(summary = "Describe a domain model", description = "Returns fields and shared methods for the domain")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Domain description"),
            @ApiResponse(responseCode = "404", description = "Domain not found")
    })
    @GetMapping("/models/{name}")
    public Mono<ResponseEntity<Map<String,Object>>> describe(
            @Parameter(description = "Domain name") @PathVariable @NotBlank String name) {
        return registry.describeDomainMono(name)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // --- CRUD for domain instances -------------------------------------------------
    @Operation(summary = "List all items in a domain")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of items"),
            @ApiResponse(responseCode = "405", description = "Read operation disabled",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/models/{name}/items")
    public Flux<Map<String,Object>> listItems(
            @Parameter(description = "Domain name") @PathVariable @NotBlank String name) {
        if (!props.isOperationAllowed(name, "read")) {
            log.warn("Read operation disabled for {}", name);
            return Flux.error(new RuntimeException("operation-disabled"));
        }
        return dataStore.list(name);
    }

    @Operation(summary = "Get a specific item by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item found"),
            @ApiResponse(responseCode = "404", description = "Item not found"),
            @ApiResponse(responseCode = "405", description = "Read operation disabled")
    })
    @GetMapping("/models/{name}/items/{id}")
    public Mono<ResponseEntity<Map<String,Object>>> getItem(
            @Parameter(description = "Domain name") @PathVariable @NotBlank String name,
            @Parameter(description = "Item ID") @PathVariable @NotBlank String id) {
        if (!props.isOperationAllowed(name, "read")) {
            log.warn("Get operation disabled for {}/{}", name, id);
            return Mono.just(ResponseEntity.status(405).body(Map.of("error", ErrorCode.OPERATION_DISABLED.getCode())));
        }
        return dataStore.get(name, id).map(ResponseEntity::ok).defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Create a new item")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Item created"),
            @ApiResponse(responseCode = "405", description = "Create operation disabled")
    })
    @PostMapping("/models/{name}/items")
    public Mono<ResponseEntity<Map<String,Object>>> createItem(
            @Parameter(description = "Domain name") @PathVariable @NotBlank String name,
            @Valid @RequestBody Map<String,Object> body) {
        if (!props.isOperationAllowed(name, "create")) {
            log.warn("Create operation disabled for {}", name);
            return Mono.just(ResponseEntity.status(405).body(Map.of("error", ErrorCode.OPERATION_DISABLED.getCode())));
        }
        return dataStore.create(name, body).map(b -> ResponseEntity.status(201).body(b));
    }

    @Operation(summary = "Update an existing item")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item updated"),
            @ApiResponse(responseCode = "404", description = "Item not found"),
            @ApiResponse(responseCode = "405", description = "Update operation disabled")
    })
    @PutMapping("/models/{name}/items/{id}")
    public Mono<ResponseEntity<Map<String,Object>>> updateItem(
            @Parameter(description = "Domain name") @PathVariable @NotBlank String name,
            @Parameter(description = "Item ID") @PathVariable @NotBlank String id,
            @Valid @RequestBody Map<String,Object> body) {
        if (!props.isOperationAllowed(name, "update")) {
            log.warn("Update operation disabled for {}/{}", name, id);
            return Mono.just(ResponseEntity.status(405).body(Map.of("error", ErrorCode.OPERATION_DISABLED.getCode())));
        }
        return dataStore.update(name, id, body).map(ResponseEntity::ok).defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Delete an item")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Item deleted"),
            @ApiResponse(responseCode = "404", description = "Item not found"),
            @ApiResponse(responseCode = "405", description = "Delete operation disabled")
    })
    @DeleteMapping("/models/{name}/items/{id}")
    public Mono<ResponseEntity<Void>> deleteItem(
            @Parameter(description = "Domain name") @PathVariable @NotBlank String name,
            @Parameter(description = "Item ID") @PathVariable @NotBlank String id) {
        if (!props.isOperationAllowed(name, "delete")) {
            log.warn("Delete operation disabled for {}/{}", name, id);
            return Mono.just(ResponseEntity.status(405).build());
        }
        return dataStore.delete(name, id).map(ok -> ok ? ResponseEntity.noContent().<Void>build() : ResponseEntity.notFound().build());
    }

}
