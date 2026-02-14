package com.honeycomb.core.web;

import com.honeycomb.core.dto.AuditEvent;
import com.honeycomb.core.service.AuditLogService;
import com.honeycomb.core.util.HoneycombConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Returns a paginated list of audit events from the in-memory audit log.
 *
 * <p>Mapped at {@code GET /honeycomb/audit}. Supports optional
 * {@code page} and {@code size} query parameters.</p>
 *
 * @see com.honeycomb.core.service.AuditLogService
 */
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
