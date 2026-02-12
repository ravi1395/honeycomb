package com.example.honeycomb.web;

import com.example.honeycomb.dto.ErrorCode;
import com.example.honeycomb.dto.ErrorResponse;
import com.example.honeycomb.service.AuditLogService;
import com.example.honeycomb.service.CellRegistry;
import com.example.honeycomb.service.CellDataStore;
import com.example.honeycomb.service.CellSchemaValidator;
import com.example.honeycomb.service.IdempotencyService;
import com.example.honeycomb.service.ServiceCellRegistry;
import com.example.honeycomb.config.HoneycombProperties;
import com.example.honeycomb.config.HoneycombIdempotencyProperties;
import com.example.honeycomb.util.HoneycombConstants;
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
import org.springframework.web.server.ServerWebExchange;


@RestController
@RequestMapping(HoneycombConstants.Paths.HONEYCOMB_BASE)
@Tag(name = HoneycombConstants.Docs.TAG_CELL_REGISTRY,
    description = HoneycombConstants.Docs.TAG_CELL_REGISTRY_DESC)
@Validated
public class HoneycombController {
    private static final Logger log = LoggerFactory.getLogger(HoneycombController.class);
    private final CellRegistry registry;
    private final CellDataStore dataStore;
    private final HoneycombProperties props;
    private final AuditLogService auditLogService;
    private final CellSchemaValidator schemaValidator;
    private final IdempotencyService idempotencyService;
    private final HoneycombIdempotencyProperties idempotencyProperties;
    private final ServiceCellRegistry serviceCellRegistry;

    public HoneycombController(CellRegistry registry,
                               CellDataStore dataStore,
                               HoneycombProperties props,
                               AuditLogService auditLogService,
                               CellSchemaValidator schemaValidator,
                               IdempotencyService idempotencyService,
                               HoneycombIdempotencyProperties idempotencyProperties,
                               ServiceCellRegistry serviceCellRegistry) {
        this.registry = registry;
        this.dataStore = dataStore;
        this.props = props;
        this.auditLogService = auditLogService;
        this.schemaValidator = schemaValidator;
        this.idempotencyService = idempotencyService;
        this.idempotencyProperties = idempotencyProperties;
        this.serviceCellRegistry = serviceCellRegistry;
    }

        @Operation(summary = HoneycombConstants.Docs.REGISTRY_LIST_MODELS)
        @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_200,
            description = HoneycombConstants.Docs.REGISTRY_LIST_MODELS_DESC)
        @GetMapping(HoneycombConstants.Names.SEPARATOR_SLASH + HoneycombConstants.Paths.MODELS)
    public Flux<String> listModels() {
        return registry.getCellNamesFlux();
    }

        @Operation(summary = HoneycombConstants.Docs.REGISTRY_DESCRIBE,
            description = HoneycombConstants.Docs.REGISTRY_DESCRIBE_DESC)
    @ApiResponses({
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_200, description = HoneycombConstants.Swagger.DESC_CELL),
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_404, description = HoneycombConstants.Swagger.DESC_CELL_NOT_FOUND)
    })
        @GetMapping(HoneycombConstants.Names.SEPARATOR_SLASH + HoneycombConstants.Paths.MODELS + "/{name}")
        public Mono<ResponseEntity<Map<String,Object>>> describe(
            @Parameter(description = HoneycombConstants.Docs.PARAM_CELL_NAME) @PathVariable @NotBlank String name) {
        return registry.describeCellMono(name)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // --- CRUD for cell instances -------------------------------------------------
        @Operation(summary = HoneycombConstants.Docs.REGISTRY_LIST_ITEMS)
    @ApiResponses({
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_200,
                description = HoneycombConstants.Docs.REGISTRY_ITEM_LIST_DESC),
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_405,
                description = HoneycombConstants.Docs.REGISTRY_READ_DISABLED_DESC,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
        @GetMapping(HoneycombConstants.Names.SEPARATOR_SLASH
            + HoneycombConstants.Paths.MODELS
            + "/{name}/"
            + HoneycombConstants.Paths.ITEMS)
    public Flux<Map<String,Object>> listItems(
            @Parameter(description = HoneycombConstants.Docs.PARAM_CELL_NAME) @PathVariable @NotBlank String name) {
        if (serviceCellRegistry != null && serviceCellRegistry.hasCell(name)) {
            return Flux.error(new RuntimeException(ErrorCode.OPERATION_DISABLED.getCode()));
        }
        if (!props.isOperationAllowed(name, HoneycombConstants.Ops.READ)) {
            log.warn(HoneycombConstants.Messages.READ_DISABLED, name);
            return Flux.error(new RuntimeException(ErrorCode.OPERATION_DISABLED.getCode()));
        }
        return dataStore.list(name);
    }

        @Operation(summary = HoneycombConstants.Docs.REGISTRY_GET_ITEM)
    @ApiResponses({
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_200,
                description = HoneycombConstants.Docs.REGISTRY_ITEM_FOUND_DESC),
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_404,
                description = HoneycombConstants.Docs.REGISTRY_ITEM_NOT_FOUND_DESC),
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_405,
                description = HoneycombConstants.Docs.REGISTRY_READ_DISABLED_DESC)
    })
        @GetMapping(HoneycombConstants.Names.SEPARATOR_SLASH
            + HoneycombConstants.Paths.MODELS
            + "/{name}/"
                + HoneycombConstants.Paths.ITEMS
                + HoneycombConstants.Paths.ID_PATH)
    public Mono<ResponseEntity<Map<String,Object>>> getItem(
            @Parameter(description = HoneycombConstants.Docs.PARAM_CELL_NAME) @PathVariable @NotBlank String name,
            @Parameter(description = HoneycombConstants.Docs.PARAM_ITEM_ID) @PathVariable @NotBlank String id) {
        if (serviceCellRegistry != null && serviceCellRegistry.hasCell(name)) {
            return Mono.just(ResponseEntity.status(405).body(Map.of(HoneycombConstants.JsonKeys.ERROR, ErrorCode.OPERATION_DISABLED.getCode())));
        }
        if (!props.isOperationAllowed(name, HoneycombConstants.Ops.READ)) {
            log.warn(HoneycombConstants.Messages.GET_DISABLED, name, id);
            return Mono.just(ResponseEntity.status(405).body(Map.of(HoneycombConstants.JsonKeys.ERROR, ErrorCode.OPERATION_DISABLED.getCode())));
        }
        return dataStore.get(name, id).map(ResponseEntity::ok).defaultIfEmpty(ResponseEntity.notFound().build());
    }

        @Operation(summary = HoneycombConstants.Docs.REGISTRY_CREATE_ITEM)
    @ApiResponses({
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_201,
                description = HoneycombConstants.Docs.REGISTRY_ITEM_CREATED_DESC),
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_405,
                description = HoneycombConstants.Docs.REGISTRY_CREATE_DISABLED_DESC)
    })
        @PostMapping(HoneycombConstants.Names.SEPARATOR_SLASH
            + HoneycombConstants.Paths.MODELS
            + "/{name}/"
            + HoneycombConstants.Paths.ITEMS)
    public Mono<ResponseEntity<Map<String,Object>>> createItem(
            @Parameter(description = HoneycombConstants.Docs.PARAM_CELL_NAME) @PathVariable @NotBlank String name,
            @Valid @RequestBody Map<String,Object> body,
            ServerWebExchange exchange) {
        if (serviceCellRegistry != null && serviceCellRegistry.hasCell(name)) {
            return Mono.just(ResponseEntity.status(405).body(Map.of(HoneycombConstants.JsonKeys.ERROR, ErrorCode.OPERATION_DISABLED.getCode())));
        }
        if (!props.isOperationAllowed(name, HoneycombConstants.Ops.CREATE)) {
            log.warn(HoneycombConstants.Messages.CREATE_DISABLED, name);
            auditLogService.record(HoneycombConstants.Audit.ACTOR_SYSTEM, HoneycombConstants.Audit.ACTION_ITEM_CREATE, name, HoneycombConstants.Status.DENIED, Map.of(HoneycombConstants.JsonKeys.REASON, HoneycombConstants.Messages.DISABLED));
            return Mono.just(ResponseEntity.status(405).body(Map.of(HoneycombConstants.JsonKeys.ERROR, ErrorCode.OPERATION_DISABLED.getCode())));
        }
        Mono<ResponseEntity<Map<String,Object>>> action = schemaValidator.validate(name, body)
                .then(dataStore.create(name, body))
                .map(b -> {
                    auditLogService.record(HoneycombConstants.Audit.ACTOR_SYSTEM, HoneycombConstants.Audit.ACTION_ITEM_CREATE, name, HoneycombConstants.Status.OK, Map.of(HoneycombConstants.JsonKeys.ID, b.get(HoneycombConstants.JsonKeys.ID)));
                    return ResponseEntity.status(201).body(b);
                });
        String key = idempotencyKey(name, HoneycombConstants.Ops.CREATE, null, exchange);
        return idempotencyService.handle(key, action);
    }

        @Operation(summary = HoneycombConstants.Docs.REGISTRY_UPDATE_ITEM)
    @ApiResponses({
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_200,
                description = HoneycombConstants.Docs.REGISTRY_ITEM_UPDATED_DESC),
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_404,
                description = HoneycombConstants.Docs.REGISTRY_ITEM_NOT_FOUND_DESC),
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_405,
                description = HoneycombConstants.Docs.REGISTRY_UPDATE_DISABLED_DESC)
    })
        @PutMapping(HoneycombConstants.Names.SEPARATOR_SLASH
            + HoneycombConstants.Paths.MODELS
            + "/{name}/"
                + HoneycombConstants.Paths.ITEMS
                + HoneycombConstants.Paths.ID_PATH)
    public Mono<ResponseEntity<Map<String,Object>>> updateItem(
            @Parameter(description = HoneycombConstants.Docs.PARAM_CELL_NAME) @PathVariable @NotBlank String name,
            @Parameter(description = HoneycombConstants.Docs.PARAM_ITEM_ID) @PathVariable @NotBlank String id,
            @Valid @RequestBody Map<String,Object> body,
            ServerWebExchange exchange) {
        if (serviceCellRegistry != null && serviceCellRegistry.hasCell(name)) {
            return Mono.just(ResponseEntity.status(405).body(Map.of(HoneycombConstants.JsonKeys.ERROR, ErrorCode.OPERATION_DISABLED.getCode())));
        }
        if (!props.isOperationAllowed(name, HoneycombConstants.Ops.UPDATE)) {
            log.warn(HoneycombConstants.Messages.UPDATE_DISABLED, name, id);
            auditLogService.record(HoneycombConstants.Audit.ACTOR_SYSTEM, HoneycombConstants.Audit.ACTION_ITEM_UPDATE, name, HoneycombConstants.Status.DENIED, Map.of(HoneycombConstants.JsonKeys.ID, id));
            return Mono.just(ResponseEntity.status(405).body(Map.of(HoneycombConstants.JsonKeys.ERROR, ErrorCode.OPERATION_DISABLED.getCode())));
        }
        Mono<ResponseEntity<Map<String,Object>>> action = schemaValidator.validate(name, body)
                .then(dataStore.update(name, id, body))
                .map(updated -> {
                    auditLogService.record(HoneycombConstants.Audit.ACTOR_SYSTEM, HoneycombConstants.Audit.ACTION_ITEM_UPDATE, name, HoneycombConstants.Status.OK, Map.of(HoneycombConstants.JsonKeys.ID, id));
                    return ResponseEntity.ok(updated);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
        String key = idempotencyKey(name, HoneycombConstants.Ops.UPDATE, id, exchange);
        return idempotencyService.handle(key, action);
    }

        @Operation(summary = HoneycombConstants.Docs.REGISTRY_DELETE_ITEM)
    @ApiResponses({
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_204,
                description = HoneycombConstants.Docs.REGISTRY_ITEM_DELETED_DESC),
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_404,
                description = HoneycombConstants.Docs.REGISTRY_ITEM_NOT_FOUND_DESC),
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_405,
                description = HoneycombConstants.Docs.REGISTRY_DELETE_DISABLED_DESC)
    })
        @DeleteMapping(HoneycombConstants.Names.SEPARATOR_SLASH
            + HoneycombConstants.Paths.MODELS
            + "/{name}/"
                + HoneycombConstants.Paths.ITEMS
                + HoneycombConstants.Paths.ID_PATH)
    public Mono<ResponseEntity<Void>> deleteItem(
            @Parameter(description = HoneycombConstants.Docs.PARAM_CELL_NAME) @PathVariable @NotBlank String name,
            @Parameter(description = HoneycombConstants.Docs.PARAM_ITEM_ID) @PathVariable @NotBlank String id) {
        if (serviceCellRegistry != null && serviceCellRegistry.hasCell(name)) {
            return Mono.just(ResponseEntity.status(405).build());
        }
        if (!props.isOperationAllowed(name, HoneycombConstants.Ops.DELETE)) {
            log.warn(HoneycombConstants.Messages.DELETE_DISABLED, name, id);
            auditLogService.record(HoneycombConstants.Audit.ACTOR_SYSTEM, HoneycombConstants.Audit.ACTION_ITEM_DELETE, name, HoneycombConstants.Status.DENIED, Map.of(HoneycombConstants.JsonKeys.ID, id));
            return Mono.just(ResponseEntity.status(405).build());
        }
        return dataStore.delete(name, id)
                .map(ok -> {
                    if (ok) {
                        auditLogService.record(HoneycombConstants.Audit.ACTOR_SYSTEM, HoneycombConstants.Audit.ACTION_ITEM_DELETE, name, HoneycombConstants.Status.OK, Map.of(HoneycombConstants.JsonKeys.ID, id));
                    } else {
                        auditLogService.record(HoneycombConstants.Audit.ACTOR_SYSTEM, HoneycombConstants.Audit.ACTION_ITEM_DELETE, name, HoneycombConstants.Status.NOT_FOUND, Map.of(HoneycombConstants.JsonKeys.ID, id));
                    }
                    return ok ? ResponseEntity.noContent().<Void>build() : ResponseEntity.notFound().build();
                });
    }

    private String idempotencyKey(String cell, String operation, String id, ServerWebExchange exchange) {
        if (exchange == null || idempotencyProperties == null) return null;
        String header = idempotencyProperties.getHeader();
        if (header == null || header.isBlank()) return null;
        String key = exchange.getRequest().getHeaders().getFirst(header);
        if (key == null || key.isBlank()) return null;
        String suffix = id == null
            ? HoneycombConstants.Messages.EMPTY
            : HoneycombConstants.Names.SEPARATOR_COLON + id;
        return cell
            + HoneycombConstants.Names.SEPARATOR_COLON
            + operation
            + suffix
            + HoneycombConstants.Names.SEPARATOR_COLON
            + key;
    }

}
