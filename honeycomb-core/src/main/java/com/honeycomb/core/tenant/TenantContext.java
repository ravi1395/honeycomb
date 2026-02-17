package com.honeycomb.core.tenant;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Optional;

/**
 * Reactor-context-based tenant holder.
 *
 * <p>The current tenant ID is stored in the Reactor {@link Context} so
 * that it flows naturally through the reactive chain without ThreadLocals.
 * Use {@link #current()} inside any reactive pipeline to obtain the
 * tenant ID set by {@link com.honeycomb.core.tenant.TenantWebFilter}.</p>
 *
 * @since 1.4.3
 */
public final class TenantContext {
    private TenantContext() {}

    /** Reactor context key for the tenant ID. */
    public static final String TENANT_CONTEXT_KEY = "honeycomb.tenantId";

    /**
     * Retrieve the current tenant ID from the Reactor context.
     *
     * @return a {@link Mono} that emits the tenant ID, or empty if none set
     */
    public static Mono<String> current() {
        return Mono.deferContextual(ctx ->
                ctx.hasKey(TENANT_CONTEXT_KEY)
                        ? Mono.just(ctx.get(TENANT_CONTEXT_KEY))
                        : Mono.empty()
        );
    }

    /**
     * Retrieve the tenant from a Reactor {@link Context} snapshot.
     */
    public static Optional<String> fromContext(Context ctx) {
        return ctx.hasKey(TENANT_CONTEXT_KEY)
                ? Optional.of(ctx.get(TENANT_CONTEXT_KEY))
                : Optional.empty();
    }

    /**
     * Write the tenant into a Reactor {@link Context}.
     */
    public static Context withTenant(Context ctx, String tenantId) {
        return ctx.put(TENANT_CONTEXT_KEY, tenantId);
    }
}
