package com.example.honeycomb.web;

import com.example.honeycomb.dto.ErrorCode;
import com.example.honeycomb.dto.ErrorResponse;
import com.example.honeycomb.service.AuditLogService;
import com.example.honeycomb.service.CellRegistry;
import com.example.honeycomb.service.CellDataStore;
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
@Tag(name = "Cell Registry", description = "CRUD operations for cell models and instances")
@Validated
public class HoneycombController {
    private static final Logger log = LoggerFactory.getLogger(HoneycombController.class);
    private final CellRegistry registry;
    private final CellDataStore dataStore;
    private final HoneycombProperties props;
    private final AuditLogService auditLogService;

    public HoneycombController(CellRegistry registry, CellDataStore dataStore, HoneycombProperties props, AuditLogService auditLogService) {
        this.registry = registry;
        this.dataStore = dataStore;
        this.props = props;
        this.auditLogService = auditLogService;
    }

    @Operation(summary = "List all registered cell models")
    @ApiResponse(responseCode = "200", description = "List of cell names")
    @GetMapping("/models")
    public Flux<String> listModels() {
        return registry.getCellNamesFlux();
    }

    @Operation(summary = "Describe a cell model", description = "Returns fields and shared methods for the cell")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cell description"),
            @ApiResponse(responseCode = "404", description = "Cell not found")
    })
    @GetMapping("/models/{name}")
    public Mono<ResponseEntity<Map<String,Object>>> describe(
            @Parameter(description = "Cell name") @PathVariable @NotBlank String name) {
        return registry.describeCellMono(name)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // --- CRUD for cell instances -------------------------------------------------
    @Operation(summary = "List all items in a cell")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of items"),
            @ApiResponse(responseCode = "405", description = "Read operation disabled",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/models/{name}/items")
    public Flux<Map<String,Object>> listItems(
            @Parameter(description = "Cell name") @PathVariable @NotBlank String name) {
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
            @Parameter(description = "Cell name") @PathVariable @NotBlank String name,
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
            @Parameter(description = "Cell name") @PathVariable @NotBlank String name,
            @Valid @RequestBody Map<String,Object> body) {
        if (!props.isOperationAllowed(name, "create")) {
            log.warn("Create operation disabled for {}", name);
            auditLogService.record("system", "item.create", name, "denied", Map.of("reason", "disabled"));
            return Mono.just(ResponseEntity.status(405).body(Map.of("error", ErrorCode.OPERATION_DISABLED.getCode())));
        }
        return dataStore.create(name, body).map(b -> {
            auditLogService.record("system", "item.create", name, "ok", Map.of("id", b.get("id")));
            return ResponseEntity.status(201).body(b);
        });
    }

    @Operation(summary = "Update an existing item")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item updated"),
            @ApiResponse(responseCode = "404", description = "Item not found"),
            @ApiResponse(responseCode = "405", description = "Update operation disabled")
    })
    @PutMapping("/models/{name}/items/{id}")
    public Mono<ResponseEntity<Map<String,Object>>> updateItem(
            @Parameter(description = "Cell name") @PathVariable @NotBlank String name,
            @Parameter(description = "Item ID") @PathVariable @NotBlank String id,
            @Valid @RequestBody Map<String,Object> body) {
        if (!props.isOperationAllowed(name, "update")) {
            log.warn("Update operation disabled for {}/{}", name, id);
            auditLogService.record("system", "item.update", name, "denied", Map.of("id", id));
            return Mono.just(ResponseEntity.status(405).body(Map.of("error", ErrorCode.OPERATION_DISABLED.getCode())));
        }
        return dataStore.update(name, id, body)
                .map(updated -> {
                    auditLogService.record("system", "item.update", name, "ok", Map.of("id", id));
                    return ResponseEntity.ok(updated);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Delete an item")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Item deleted"),
            @ApiResponse(responseCode = "404", description = "Item not found"),
            @ApiResponse(responseCode = "405", description = "Delete operation disabled")
    })
    @DeleteMapping("/models/{name}/items/{id}")
    public Mono<ResponseEntity<Void>> deleteItem(
            @Parameter(description = "Cell name") @PathVariable @NotBlank String name,
            @Parameter(description = "Item ID") @PathVariable @NotBlank String id) {
        if (!props.isOperationAllowed(name, "delete")) {
            log.warn("Delete operation disabled for {}/{}", name, id);
            auditLogService.record("system", "item.delete", name, "denied", Map.of("id", id));
            return Mono.just(ResponseEntity.status(405).build());
        }
        return dataStore.delete(name, id)
                .map(ok -> {
                    if (ok) {
                        auditLogService.record("system", "item.delete", name, "ok", Map.of("id", id));
                    } else {
                        auditLogService.record("system", "item.delete", name, "not-found", Map.of("id", id));
                    }
                    return ok ? ResponseEntity.noContent().<Void>build() : ResponseEntity.notFound().build();
                });
    }

}
