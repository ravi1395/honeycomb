package com.honeycomb.core.tenant;

import com.honeycomb.core.service.CellDataStore;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Decorator around any {@link CellDataStore} that automatically scopes
 * all operations by prepending the current tenant ID to the cell name.
 *
 * <p>This effectively gives each tenant its own isolated namespace
 * ({@code tenantA::orders}, {@code tenantB::orders}) while reusing
 * the same underlying storage backend.</p>
 *
 * @since 1.4.3
 */
public class TenantAwareCellDataStore implements CellDataStore {

    private final CellDataStore delegate;

    public TenantAwareCellDataStore(CellDataStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public Flux<Map<String, Object>> list(String cell) {
        return TenantContext.current()
                .flatMapMany(tenant -> delegate.list(scopedCell(tenant, cell)))
                .switchIfEmpty(Flux.defer(() -> delegate.list(cell)));
    }

    @Override
    public Mono<Map<String, Object>> get(String cell, String id) {
        return TenantContext.current()
                .flatMap(tenant -> delegate.get(scopedCell(tenant, cell), id))
                .switchIfEmpty(Mono.defer(() -> delegate.get(cell, id)));
    }

    @Override
    public Mono<Map<String, Object>> create(String cell, Map<String, Object> payload) {
        return TenantContext.current()
                .flatMap(tenant -> delegate.create(scopedCell(tenant, cell), payload))
                .switchIfEmpty(Mono.defer(() -> delegate.create(cell, payload)));
    }

    @Override
    public Mono<Map<String, Object>> update(String cell, String id, Map<String, Object> payload) {
        return TenantContext.current()
                .flatMap(tenant -> delegate.update(scopedCell(tenant, cell), id, payload))
                .switchIfEmpty(Mono.defer(() -> delegate.update(cell, id, payload)));
    }

    @Override
    public Mono<Boolean> delete(String cell, String id) {
        return TenantContext.current()
                .flatMap(tenant -> delegate.delete(scopedCell(tenant, cell), id))
                .switchIfEmpty(Mono.defer(() -> delegate.delete(cell, id)));
    }

    /** Scope cell name: {@code tenantId::cellName} */
    private static String scopedCell(String tenant, String cell) {
        return tenant + "::" + cell;
    }
}
