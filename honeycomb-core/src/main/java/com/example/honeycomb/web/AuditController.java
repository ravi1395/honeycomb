package com.example.honeycomb.web;

import com.example.honeycomb.dto.AuditEvent;
import com.example.honeycomb.service.AuditLogService;
import com.example.honeycomb.util.HoneycombConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping(HoneycombConstants.Paths.HONEYCOMB_AUDIT)
@Tag(name = HoneycombConstants.Docs.TAG_AUDIT, description = HoneycombConstants.Docs.TAG_AUDIT_DESC)
public class AuditController {
    private final AuditLogService auditLogService;

    public AuditController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Operation(summary = HoneycombConstants.Docs.AUDIT_LIST)
    @GetMapping
    public Flux<AuditEvent> list(@RequestParam(name = HoneycombConstants.Params.LIMIT,
            defaultValue = HoneycombConstants.Defaults.AUDIT_LIMIT) int limit) {
        return Flux.fromIterable(auditLogService.list(limit));
    }
}
