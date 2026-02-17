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
                .map(tenant -> scopedCell(tenant, cell))
                .defaultIfEmpty(cell)
                .flatMapMany(delegate::list);
    }

    @Override
    public Mono<Map<String, Object>> get(String cell, String id) {
        return TenantContext.current()
                .map(tenant -> scopedCell(tenant, cell))
                .defaultIfEmpty(cell)
                .flatMap(resolved -> delegate.get(resolved, id));
    }

    @Override
    public Mono<Map<String, Object>> create(String cell, Map<String, Object> payload) {
        return TenantContext.current()
                .map(tenant -> scopedCell(tenant, cell))
                .defaultIfEmpty(cell)
                .flatMap(resolved -> delegate.create(resolved, payload));
    }

    @Override
    public Mono<Map<String, Object>> update(String cell, String id, Map<String, Object> payload) {
        return TenantContext.current()
                .map(tenant -> scopedCell(tenant, cell))
                .defaultIfEmpty(cell)
                .flatMap(resolved -> delegate.update(resolved, id, payload));
    }

    @Override
    public Mono<Boolean> delete(String cell, String id) {
        return TenantContext.current()
                .map(tenant -> scopedCell(tenant, cell))
                .defaultIfEmpty(cell)
                .flatMap(resolved -> delegate.delete(resolved, id));
    }

    /** Scope cell name: {@code tenantId::cellName} */
    private static String scopedCell(String tenant, String cell) {
        return tenant + "::" + cell;
    }
}
