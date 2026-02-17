package com.honeycomb.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.honeycomb.core.config.HoneycombProperties;
import com.honeycomb.core.config.HoneycombSecurityProperties;
import com.honeycomb.core.config.HoneycombRateLimiterProperties;
import com.honeycomb.core.config.HoneycombRoutingProperties;
import com.honeycomb.core.config.HoneycombAutoscaleProperties;
import com.honeycomb.core.config.HoneycombAuditProperties;
import com.honeycomb.core.config.HoneycombIdempotencyProperties;
import com.honeycomb.core.config.HoneycombValidationProperties;
import com.honeycomb.core.config.HoneycombSharedMethodProperties;
import com.honeycomb.core.config.HoneycombEventProperties;
import com.honeycomb.core.config.HoneycombCacheProperties;
import com.honeycomb.core.config.HoneycombVersioningProperties;
import com.honeycomb.core.config.HoneycombLockingProperties;
import com.honeycomb.core.config.HoneycombContractProperties;
import com.honeycomb.core.config.HoneycombTenantProperties;

@SpringBootApplication
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
    HoneycombEventProperties.class,
    HoneycombCacheProperties.class,
    HoneycombVersioningProperties.class,
    HoneycombLockingProperties.class,
    HoneycombContractProperties.class,
    HoneycombTenantProperties.class
})
public class HoneycombApplication {
    public static void main(String[] args) {
        SpringApplication.run(HoneycombApplication.class, args);
    }
}
