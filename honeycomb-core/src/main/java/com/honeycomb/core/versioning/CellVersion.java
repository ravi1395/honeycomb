package com.honeycomb.core.versioning;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the version of a {@link com.honeycomb.core.annotations.Cell}.
 *
 * <p>When cell versioning is enabled, multiple classes annotated with {@code @Cell}
 * can share the same cell name as long as they declare different versions.
 * The framework registers each versioned variant and routes traffic according
 * to the configured traffic-split weights.</p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * @Cell("catalog")
 * @CellVersion("v1")
 * public class CatalogCellV1 { ... }
 *
 * @Cell("catalog")
 * @CellVersion("v2")
 * public class CatalogCellV2 { ... }
 * }</pre>
 *
 * @since 1.4.2
 * @see com.honeycomb.core.config.HoneycombVersioningProperties
 * @see com.honeycomb.core.service.CellVersioningService
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CellVersion {

    /**
     * The version label for this cell variant (e.g. {@code "v1"}, {@code "v2"}).
     */
    String value();
}
