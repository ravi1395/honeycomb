package com.honeycomb.core.web;

import com.honeycomb.core.config.HoneycombTenantProperties;
import com.honeycomb.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing multi-tenancy management endpoints.
 *
 * <p>Activated only when {@code honeycomb.tenant.enabled=true}.</p>
 *
 * @since 1.4.3
 */
@RestController
@RequestMapping("/honeycomb/tenants")
@ConditionalOnProperty(name = "honeycomb.tenant.enabled", havingValue = "true")
@Tag(name = "Multi-Tenancy", description = "Multi-tenancy management and tenant info")
public class TenantController {

    private final HoneycombTenantProperties props;

    public TenantController(HoneycombTenantProperties props) {
        this.props = props;
    }

    @GetMapping("/current")
    @Operation(summary = "Get the tenant ID resolved for the current request")
    public Mono<ResponseEntity<Map<String, Object>>> currentTenant() {
        return TenantContext.current()
                .map(tenant -> ResponseEntity.ok(Map.<String, Object>of(
                        "tenantId", tenant,
                        "header", props.getHeaderName()
                )))
                .defaultIfEmpty(ResponseEntity.ok(Map.of(
                        "tenantId", "none",
                        "header", props.getHeaderName()
                )));
    }

    @GetMapping("/allowed")
    @Operation(summary = "List allowed tenant IDs (empty = all allowed)")
    public Mono<ResponseEntity<Map<String, Object>>> allowedTenants() {
        List<String> allowed = props.getAllowedTenants();
        return Mono.just(ResponseEntity.ok(Map.<String, Object>of(
                "allowedTenants", allowed,
                "enforceHeader", props.isEnforceHeader(),
                "unrestricted", allowed.isEmpty()
        )));
    }

    @GetMapping("/config")
    @Operation(summary = "Get multi-tenancy configuration")
    public Mono<ResponseEntity<Map<String, Object>>> config() {
        return Mono.just(ResponseEntity.ok(Map.<String, Object>of(
                "enabled", props.isEnabled(),
                "headerName", props.getHeaderName(),
                "defaultTenant", props.getDefaultTenant(),
                "enforceHeader", props.isEnforceHeader(),
                "scopeMetrics", props.isScopeMetrics(),
                "storageKeyTemplate", props.getStorageKeyTemplate()
        )));
    }
}
