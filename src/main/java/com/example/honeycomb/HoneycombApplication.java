package com.example.honeycomb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.example.honeycomb.config.HoneycombProperties;
import com.example.honeycomb.config.HoneycombSecurityProperties;
import com.example.honeycomb.config.HoneycombRateLimiterProperties;
import com.example.honeycomb.config.HoneycombRoutingProperties;
import com.example.honeycomb.config.HoneycombAutoscaleProperties;
import com.example.honeycomb.config.HoneycombAuditProperties;
import com.example.honeycomb.config.HoneycombIdempotencyProperties;
import com.example.honeycomb.config.HoneycombValidationProperties;

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
    HoneycombIdempotencyProperties.class
})
public class HoneycombApplication {
    public static void main(String[] args) {
        SpringApplication.run(HoneycombApplication.class, args);
    }
}
