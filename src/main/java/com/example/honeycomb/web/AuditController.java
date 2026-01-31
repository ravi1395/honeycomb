package com.example.honeycomb.web;

import com.example.honeycomb.dto.AuditEvent;
import com.example.honeycomb.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/honeycomb/audit")
@Tag(name = "Audit", description = "Audit log and event history")
public class AuditController {
    private final AuditLogService auditLogService;

    public AuditController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Operation(summary = "List recent audit events")
    @GetMapping
    public Flux<AuditEvent> list(@RequestParam(name = "limit", defaultValue = "100") int limit) {
        return Flux.fromIterable(auditLogService.list(limit));
    }
}
