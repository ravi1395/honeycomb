package com.honeycomb.starter;

import com.honeycomb.core.HoneycombApplication;
import com.honeycomb.core.config.HoneycombAutoscaleProperties;
import com.honeycomb.core.config.HoneycombAuditProperties;
import com.honeycomb.core.config.HoneycombIdempotencyProperties;
import com.honeycomb.core.config.HoneycombCacheProperties;
import com.honeycomb.core.config.HoneycombEventProperties;
import com.honeycomb.core.config.HoneycombProperties;
import com.honeycomb.core.config.HoneycombRateLimiterProperties;
import com.honeycomb.core.config.HoneycombRoutingProperties;
import com.honeycomb.core.config.HoneycombSecurityProperties;
import com.honeycomb.core.config.HoneycombValidationProperties;
import com.honeycomb.core.config.HoneycombSharedMethodProperties;
import com.honeycomb.core.config.HoneycombVersioningProperties;
import com.honeycomb.core.config.HoneycombLockingProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot auto-configuration entry point for the Honeycomb framework.
 *
 * <p>Enables all Honeycomb configuration properties and component-scans the
 * {@code com.honeycomb.core} package for controllers, services, and config classes.</p>
 *
 * <p><b>v1.3 additions:</b> {@link HoneycombEventProperties} (event bus config)
 * and {@link HoneycombCacheProperties} (distributed cache config) are now
 * included in the {@code @EnableConfigurationProperties} set.</p>
 *
 * <p><b>v1.4.2 additions:</b> {@link HoneycombVersioningProperties} (cell versioning &amp;
 * blue-green dispatch) and {@link HoneycombLockingProperties} (distributed locking &amp;
 * leader election) are now included.</p>
 */
@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties({
        HoneycombProperties.class,
        HoneycombSecurityProperties.class,
        HoneycombRateLimiterProperties.class,
        HoneycombRoutingProperties.class,
        HoneycombAutoscaleProperties.class,
        HoneycombAuditProperties.class,
        HoneycombValidationProperties.class,
        HoneycombIdempotencyProperties.class,
        HoneycombSharedMethodProperties.class,
        HoneycombEventProperties.class,        // v1.3: event-driven cell communication
        HoneycombCacheProperties.class,         // v1.3: distributed Redis cache sync
        HoneycombVersioningProperties.class,    // v1.4: cell versioning & blue-green dispatch
        HoneycombLockingProperties.class         // v1.4: distributed locking & leader election
})
@ComponentScan(
        basePackages = "com.honeycomb.core",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = HoneycombApplication.class)
)
public class HoneycombAutoConfiguration {
}
