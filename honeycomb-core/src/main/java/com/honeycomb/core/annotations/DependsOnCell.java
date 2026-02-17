package com.honeycomb.core.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a {@link Cell} depends on one or more other cells.
 *
 * <p>At startup, Honeycomb validates that all declared dependencies
 * are present in the {@link com.honeycomb.core.service.CellRegistry}.
 * If a required dependency is missing, startup fails with a clear error.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @Cell(name = "OrderService")
 * @DependsOnCell({"InventoryService", "PaymentService"})
 * public class OrderService {
 *     @Sharedwall
 *     public OrderResult placeOrder(OrderRequest request) { ... }
 * }
 * }</pre>
 *
 * @since 1.5.0
 * @see Cell
 * @see com.honeycomb.core.service.CellDependencyValidator
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DependsOnCell {

    /**
     * Names of the cells this cell depends on.
     * These must match the {@link Cell#name()} of registered cells.
     */
    String[] value();

    /**
     * Whether the dependency is required (fail startup) or optional (log warning).
     */
    boolean required() default true;
}
