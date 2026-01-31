package com.example.honeycomb.example;

import com.example.honeycomb.config.HoneycombAutoscaleProperties;
import com.example.honeycomb.config.HoneycombAuditProperties;
import com.example.honeycomb.config.HoneycombProperties;
import com.example.honeycomb.config.HoneycombRateLimiterProperties;
import com.example.honeycomb.config.HoneycombRoutingProperties;
import com.example.honeycomb.config.HoneycombSecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    scanBasePackages = {"com.example.honeycomb", "com.example.honeycomb.example"},
    excludeName = {
        "org.springframework.cloud.client.discovery.simple.reactive.SimpleReactiveDiscoveryClientAutoConfiguration",
        "org.springframework.cloud.client.discovery.composite.reactive.ReactiveCompositeDiscoveryClientAutoConfiguration"
    }
)
@EnableScheduling
@EnableConfigurationProperties({
        HoneycombProperties.class,
        HoneycombSecurityProperties.class,
        HoneycombRateLimiterProperties.class,
        HoneycombRoutingProperties.class,
        HoneycombAutoscaleProperties.class,
        HoneycombAuditProperties.class
})
public class HoneycombExampleApplication {
    private static final Logger log = LoggerFactory.getLogger(HoneycombExampleApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(HoneycombExampleApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logServerPort(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        String port = env.getProperty("local.server.port", env.getProperty("server.port", "8080"));
        log.info("Spring Boot started on port {}", port);
    }
}
