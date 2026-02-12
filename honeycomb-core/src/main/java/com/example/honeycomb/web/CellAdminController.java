package com.example.honeycomb.web;

import com.example.honeycomb.dto.CellRuntimeStatus;
import com.example.honeycomb.service.AuditLogService;
import com.example.honeycomb.service.CellRegistry;
import com.example.honeycomb.service.CellServerManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import com.example.honeycomb.util.HoneycombConstants;

@RestController
@RequestMapping(HoneycombConstants.Paths.HONEYCOMB_BASE
    + HoneycombConstants.Names.SEPARATOR_SLASH
    + HoneycombConstants.Paths.CELLS)
@Tag(name = HoneycombConstants.Docs.TAG_CELL_ADMIN,
    description = HoneycombConstants.Docs.TAG_CELL_ADMIN_DESC)
@Validated
public class CellAdminController {
    private final CellRegistry registry;
    private final CellServerManager serverManager;
    private final AuditLogService auditLogService;

    public CellAdminController(CellRegistry registry, CellServerManager serverManager, AuditLogService auditLogService) {
        this.registry = registry;
        this.serverManager = serverManager;
        this.auditLogService = auditLogService;
    }

        @Operation(summary = HoneycombConstants.Docs.CELL_ADMIN_LIST)
        @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_200,
            description = HoneycombConstants.Docs.CELL_ADMIN_LIST_DESC)
    @GetMapping
    public Flux<CellRuntimeStatus> listCells() {
        return Flux.fromIterable(serverManager.listCellStatuses());
    }

        @Operation(summary = HoneycombConstants.Docs.CELL_ADMIN_GET)
    @ApiResponses({
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_200,
                description = HoneycombConstants.Docs.CELL_ADMIN_STATUS_DESC),
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_404,
                description = HoneycombConstants.Docs.CELL_ADMIN_NOT_FOUND_DESC)
    })
        @GetMapping(HoneycombConstants.Paths.NAME_PATH)
    public Mono<ResponseEntity<CellRuntimeStatus>> getCell(@PathVariable String name) {
        if (registry.getCellClass(name).isEmpty()) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        return Mono.just(serverManager.getCellStatus(name)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build()));
    }

        @Operation(summary = HoneycombConstants.Docs.CELL_ADMIN_START)
    @ApiResponses({
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_200,
                description = HoneycombConstants.Docs.CELL_ADMIN_STARTED_DESC),
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_400,
                description = HoneycombConstants.Docs.CELL_ADMIN_NO_PORT_DESC),
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_404,
                description = HoneycombConstants.Docs.CELL_ADMIN_NOT_FOUND_DESC)
    })
        @PostMapping(HoneycombConstants.Paths.NAME_START)
    public Mono<ResponseEntity<Map<String, Object>>> startCell(@PathVariable String name) {
        if (registry.getCellClass(name).isEmpty()) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        return serverManager.startCellServerReactive(name)
                .map(started -> {
                    if (!started) {
                        auditLogService.record(HoneycombConstants.Audit.ACTOR_SYSTEM, HoneycombConstants.Audit.ACTION_CELL_START, name, HoneycombConstants.Status.ERROR, Map.of(HoneycombConstants.JsonKeys.REASON, HoneycombConstants.ErrorKeys.NO_CONFIGURED_PORT));
                        return ResponseEntity.badRequest().body(Map.of(HoneycombConstants.JsonKeys.ERROR, HoneycombConstants.ErrorKeys.NO_CONFIGURED_PORT));
                    }
                    auditLogService.record(HoneycombConstants.Audit.ACTOR_SYSTEM, HoneycombConstants.Audit.ACTION_CELL_START, name, HoneycombConstants.Status.OK, Map.of());
                    return ResponseEntity.ok(Map.of(HoneycombConstants.JsonKeys.STATUS, HoneycombConstants.Messages.STARTED));
                });
    }

        @Operation(summary = HoneycombConstants.Docs.CELL_ADMIN_STOP)
    @ApiResponses({
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_200,
                description = HoneycombConstants.Docs.CELL_ADMIN_STOPPED_DESC),
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_404,
                description = HoneycombConstants.Docs.CELL_ADMIN_NOT_FOUND_DESC)
    })
        @PostMapping(HoneycombConstants.Paths.NAME_STOP)
    public Mono<ResponseEntity<Map<String, Object>>> stopCell(@PathVariable String name) {
        if (registry.getCellClass(name).isEmpty()) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        return serverManager.stopCellServerReactive(name)
                .map(stopped -> {
                    auditLogService.record(HoneycombConstants.Audit.ACTOR_SYSTEM, HoneycombConstants.Audit.ACTION_CELL_STOP, name, stopped ? HoneycombConstants.Status.OK : HoneycombConstants.Status.NOOP, Map.of());
                    return ResponseEntity.ok(Map.of(HoneycombConstants.JsonKeys.STATUS, stopped ? HoneycombConstants.Messages.STOPPED : HoneycombConstants.Messages.ALREADY_STOPPED));
                });
    }

        @Operation(summary = HoneycombConstants.Docs.CELL_ADMIN_RESTART)
    @ApiResponses({
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_200,
                description = HoneycombConstants.Docs.CELL_ADMIN_RESTARTED_DESC),
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_400,
                description = HoneycombConstants.Docs.CELL_ADMIN_NO_PORT_DESC),
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_404,
                description = HoneycombConstants.Docs.CELL_ADMIN_NOT_FOUND_DESC)
    })
        @PostMapping(HoneycombConstants.Paths.NAME_RESTART)
    public Mono<ResponseEntity<Map<String, Object>>> restartCell(@PathVariable String name) {
        if (registry.getCellClass(name).isEmpty()) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        return serverManager.restartCellServerReactive(name)
                .map(restarted -> {
                    if (!restarted) {
                        auditLogService.record(HoneycombConstants.Audit.ACTOR_SYSTEM, HoneycombConstants.Audit.ACTION_CELL_RESTART, name, HoneycombConstants.Status.ERROR, Map.of(HoneycombConstants.JsonKeys.REASON, HoneycombConstants.ErrorKeys.NO_CONFIGURED_PORT));
                        return ResponseEntity.badRequest().body(Map.of(HoneycombConstants.JsonKeys.ERROR, HoneycombConstants.ErrorKeys.NO_CONFIGURED_PORT));
                    }
                    auditLogService.record(HoneycombConstants.Audit.ACTOR_SYSTEM, HoneycombConstants.Audit.ACTION_CELL_RESTART, name, HoneycombConstants.Status.OK, Map.of());
                    return ResponseEntity.ok(Map.of(HoneycombConstants.JsonKeys.STATUS, HoneycombConstants.Messages.RESTARTED));
                });
    }
}
