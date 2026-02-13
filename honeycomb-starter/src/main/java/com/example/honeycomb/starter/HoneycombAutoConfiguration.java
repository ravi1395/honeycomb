package com.example.honeycomb.starter;

import com.example.honeycomb.HoneycombApplication;
import com.example.honeycomb.config.HoneycombAutoscaleProperties;
import com.example.honeycomb.config.HoneycombAuditProperties;
import com.example.honeycomb.config.HoneycombIdempotencyProperties;
import com.example.honeycomb.config.HoneycombProperties;
import com.example.honeycomb.config.HoneycombRateLimiterProperties;
import com.example.honeycomb.config.HoneycombRoutingProperties;
import com.example.honeycomb.config.HoneycombSecurityProperties;
import com.example.honeycomb.config.HoneycombValidationProperties;
import com.example.honeycomb.config.HoneycombSharedMethodProperties;
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
        basePackages = "com.example.honeycomb",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = HoneycombApplication.class)
)
public class HoneycombAutoConfiguration {
}
