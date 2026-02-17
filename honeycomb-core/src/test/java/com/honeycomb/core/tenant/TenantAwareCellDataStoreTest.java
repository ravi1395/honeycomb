package com.honeycomb.core.tenant;

import com.honeycomb.core.service.CellDataStore;
import com.honeycomb.core.service.InMemoryCellDataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TenantAwareCellDataStore}.
 *
 * @since 1.4.3
 */
class TenantAwareCellDataStoreTest {

    private CellDataStore delegate;
    private TenantAwareCellDataStore store;

    @BeforeEach
    void setUp() {
        delegate = new InMemoryCellDataStore();
        store = new TenantAwareCellDataStore(delegate);
    }

    @Test
    @DisplayName("scopes create/get by tenant")
    void createAndGetScopedByTenant() {
        // Create item as tenant "alpha"
        StepVerifier.create(
                store.create("Orders", Map.of("id", "o1", "item", "widget"))
                        .contextWrite(ctx -> TenantContext.withTenant(ctx, "alpha"))
        ).assertNext(result -> {
            assertEquals("o1", result.get("id"));
            assertEquals("widget", result.get("item"));
        }).verifyComplete();

        // Retrieve as same tenant — should find it
        StepVerifier.create(
                store.get("Orders", "o1")
                        .contextWrite(ctx -> TenantContext.withTenant(ctx, "alpha"))
        ).assertNext(result -> assertEquals("widget", result.get("item")))
         .verifyComplete();

        // Retrieve as different tenant — should NOT find it
        StepVerifier.create(
                store.get("Orders", "o1")
                        .contextWrite(ctx -> TenantContext.withTenant(ctx, "beta"))
        ).verifyComplete(); // empty
    }

    @Test
    @DisplayName("list returns only items for the current tenant")
    void listScopedByTenant() {
        // Create items for two tenants
        store.create("Products", Map.of("id", "p1", "name", "A"))
                .contextWrite(ctx -> TenantContext.withTenant(ctx, "t1"))
                .block();
        store.create("Products", Map.of("id", "p2", "name", "B"))
                .contextWrite(ctx -> TenantContext.withTenant(ctx, "t1"))
                .block();
        store.create("Products", Map.of("id", "p3", "name", "C"))
                .contextWrite(ctx -> TenantContext.withTenant(ctx, "t2"))
                .block();

        // tenant t1 sees 2 items
        StepVerifier.create(
                store.list("Products")
                        .contextWrite(ctx -> TenantContext.withTenant(ctx, "t1"))
        ).expectNextCount(2).verifyComplete();

        // tenant t2 sees 1 item
        StepVerifier.create(
                store.list("Products")
                        .contextWrite(ctx -> TenantContext.withTenant(ctx, "t2"))
        ).expectNextCount(1).verifyComplete();
    }

    @Test
    @DisplayName("update is scoped to tenant")
    void updateScopedByTenant() {
        store.create("Items", Map.of("id", "i1", "val", "old"))
                .contextWrite(ctx -> TenantContext.withTenant(ctx, "tenantA"))
                .block();

        StepVerifier.create(
                store.update("Items", "i1", Map.of("val", "new"))
                        .contextWrite(ctx -> TenantContext.withTenant(ctx, "tenantA"))
        ).assertNext(result -> assertEquals("new", result.get("val")))
         .verifyComplete();

        // Different tenant can't update it (returns empty)
        StepVerifier.create(
                store.update("Items", "i1", Map.of("val", "hack"))
                        .contextWrite(ctx -> TenantContext.withTenant(ctx, "tenantB"))
        ).verifyComplete(); // empty — item doesn't exist for tenantB
    }

    @Test
    @DisplayName("delete is scoped to tenant")
    void deleteScopedByTenant() {
        store.create("Items", Map.of("id", "d1", "val", "x"))
                .contextWrite(ctx -> TenantContext.withTenant(ctx, "owner"))
                .block();

        // Another tenant can't delete it
        StepVerifier.create(
                store.delete("Items", "d1")
                        .contextWrite(ctx -> TenantContext.withTenant(ctx, "stranger"))
        ).expectNext(false).verifyComplete();

        // Owner can delete it
        StepVerifier.create(
                store.delete("Items", "d1")
                        .contextWrite(ctx -> TenantContext.withTenant(ctx, "owner"))
        ).expectNext(true).verifyComplete();
    }

    @Test
    @DisplayName("falls back to unscoped when no tenant in context")
    void fallsBackWhenNoTenant() {
        // Create without tenant
        delegate.create("Raw", Map.of("id", "r1", "val", "direct")).block();

        // Access via tenant-aware store without tenant context — falls back
        StepVerifier.create(store.get("Raw", "r1"))
                .assertNext(result -> assertEquals("direct", result.get("val")))
                .verifyComplete();
    }
}
