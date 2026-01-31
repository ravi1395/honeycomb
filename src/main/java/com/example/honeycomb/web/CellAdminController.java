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

@RestController
@RequestMapping("/honeycomb/cells")
@Tag(name = "Cell Administration", description = "List and manage cell servers")
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

    @Operation(summary = "List all cells with runtime status")
    @ApiResponse(responseCode = "200", description = "List of cells")
    @GetMapping
    public Flux<CellRuntimeStatus> listCells() {
        return Flux.fromIterable(serverManager.listCellStatuses());
    }

    @Operation(summary = "Get runtime status for a cell")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cell status"),
            @ApiResponse(responseCode = "404", description = "Cell not found")
    })
    @GetMapping("/{name}")
    public Mono<ResponseEntity<CellRuntimeStatus>> getCell(@PathVariable String name) {
        if (registry.getCellClass(name).isEmpty()) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        return Mono.just(serverManager.getCellStatus(name)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build()));
    }

    @Operation(summary = "Start a cell server")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cell started"),
            @ApiResponse(responseCode = "400", description = "Cell has no configured port"),
            @ApiResponse(responseCode = "404", description = "Cell not found")
    })
    @PostMapping("/{name}/start")
    public Mono<ResponseEntity<Map<String, Object>>> startCell(@PathVariable String name) {
        if (registry.getCellClass(name).isEmpty()) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        return serverManager.startCellServerReactive(name)
                .map(started -> {
                    if (!started) {
                        auditLogService.record("system", "cell.start", name, "error", Map.of("reason", "no-configured-port"));
                        return ResponseEntity.badRequest().body(Map.of("error", "no-configured-port"));
                    }
                    auditLogService.record("system", "cell.start", name, "ok", Map.of());
                    return ResponseEntity.ok(Map.of("status", "started"));
                });
    }

    @Operation(summary = "Stop a cell server")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cell stopped"),
            @ApiResponse(responseCode = "404", description = "Cell not found")
    })
    @PostMapping("/{name}/stop")
    public Mono<ResponseEntity<Map<String, Object>>> stopCell(@PathVariable String name) {
        if (registry.getCellClass(name).isEmpty()) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        return serverManager.stopCellServerReactive(name)
                .map(stopped -> {
                    auditLogService.record("system", "cell.stop", name, stopped ? "ok" : "noop", Map.of());
                    return ResponseEntity.ok(Map.of("status", stopped ? "stopped" : "already-stopped"));
                });
    }

    @Operation(summary = "Restart a cell server")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cell restarted"),
            @ApiResponse(responseCode = "400", description = "Cell has no configured port"),
            @ApiResponse(responseCode = "404", description = "Cell not found")
    })
    @PostMapping("/{name}/restart")
    public Mono<ResponseEntity<Map<String, Object>>> restartCell(@PathVariable String name) {
        if (registry.getCellClass(name).isEmpty()) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        return serverManager.restartCellServerReactive(name)
                .map(restarted -> {
                    if (!restarted) {
                        auditLogService.record("system", "cell.restart", name, "error", Map.of("reason", "no-configured-port"));
                        return ResponseEntity.badRequest().body(Map.of("error", "no-configured-port"));
                    }
                    auditLogService.record("system", "cell.restart", name, "ok", Map.of());
                    return ResponseEntity.ok(Map.of("status", "restarted"));
                });
    }
}
