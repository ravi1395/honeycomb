package com.honeycomb.starter;

import com.honeycomb.core.HoneycombApplication;
import com.honeycomb.core.config.HoneycombAutoscaleProperties;
import com.honeycomb.core.config.HoneycombAuditProperties;
import com.honeycomb.core.config.HoneycombIdempotencyProperties;
import com.honeycomb.core.config.HoneycombProperties;
import com.honeycomb.core.config.HoneycombRateLimiterProperties;
import com.honeycomb.core.config.HoneycombRoutingProperties;
import com.honeycomb.core.config.HoneycombSecurityProperties;
import com.honeycomb.core.config.HoneycombValidationProperties;
import com.honeycomb.core.config.HoneycombSharedMethodProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

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
        HoneycombSharedMethodProperties.class
})
@ComponentScan(
        basePackages = "com.honeycomb.core",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = HoneycombApplication.class)
)
public class HoneycombAutoConfiguration {
}
