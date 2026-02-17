package com.honeycomb.core.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry tracing configuration for Honeycomb.
 *
 * <p>When {@code honeycomb.tracing.enabled=true} and the Micrometer OTel bridge
 * is on the classpath, this configuration activates distributed tracing with
 * automatic span creation for all observed operations.</p>
 *
 * <p>The actual OTLP exporter and sampling are configured via Spring Boot's
 * standard {@code management.tracing.*} and {@code management.otlp.*} properties,
 * which are bridged from {@link HoneycombTracingProperties}.</p>
 *
 * @since 1.5.0
 */
@Configuration
@ConditionalOnProperty(name = "honeycomb.tracing.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(name = "io.micrometer.tracing.bridge.otel.OtelTracer")
public class TracingConfig {

    private static final Logger log = LoggerFactory.getLogger(TracingConfig.class);

    /**
     * Enables {@code @Observed} annotation support for automatic span creation.
     */
    @Bean
    @ConditionalOnClass(name = "io.micrometer.observation.aop.ObservedAspect")
    public ObservedAspect observedAspect(ObservationRegistry registry) {
        log.info("Honeycomb OpenTelemetry tracing activated with ObservedAspect support");
        return new ObservedAspect(registry);
    }
}
