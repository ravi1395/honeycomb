package com.honeycomb.grpc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.honeycomb.core.service.CellDataStore;
import com.honeycomb.core.service.SharedwallMethodCache;
import com.honeycomb.grpc.client.GrpcSharedwallClient;
import com.honeycomb.grpc.client.HoneycombGrpcClientInterceptor;
import com.honeycomb.grpc.server.CellGrpcService;
import com.honeycomb.grpc.server.HealthGrpcService;
import com.honeycomb.grpc.server.HoneycombGrpcServerInterceptor;
import com.honeycomb.grpc.server.SharedwallGrpcService;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration for the Honeycomb gRPC transport module.
 *
 * <p>Activated when {@code honeycomb.grpc.enabled=true} and gRPC classes are
 * on the classpath. Registers:</p>
 * <ul>
 *   <li>gRPC server services ({@link SharedwallGrpcService}, {@link CellGrpcService},
 *       {@link HealthGrpcService})</li>
 *   <li>gRPC server interceptor for metadata extraction</li>
 *   <li>gRPC client beans and interceptors</li>
 * </ul>
 *
 * <p>Transport mode ({@code honeycomb.grpc.transport}) controls which components
 * are activated:</p>
 * <ul>
 *   <li>{@code grpc} or {@code both} — server services are registered</li>
 *   <li>{@code http} — only client beans are registered (for calling gRPC endpoints
 *       on other cells while serving HTTP locally)</li>
 * </ul>
 *
 * @since 1.4.0
 */
@AutoConfiguration
@ConditionalOnClass(name = "io.grpc.Server")
@ConditionalOnProperty(name = "honeycomb.grpc.enabled", havingValue = "true")
@EnableConfigurationProperties(HoneycombGrpcProperties.class)
public class HoneycombGrpcAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(HoneycombGrpcAutoConfiguration.class);

    // ──────────────────── Server-Side Beans ────────────────────

    /**
     * Server-side configuration — activated when transport is GRPC or BOTH.
     */
    @Configuration
    @ConditionalOnProperty(
            name = "honeycomb.grpc.transport",
            havingValue = "grpc",
            matchIfMissing = false
    )
    static class GrpcOnlyServerConfig {

        @Bean
        @ConditionalOnMissingBean
        SharedwallGrpcService sharedwallGrpcService(SharedwallMethodCache methodCache,
                                                     ObjectMapper objectMapper) {
            log.info("Registering Honeycomb gRPC SharedwallService (transport=grpc)");
            return new SharedwallGrpcService(methodCache, objectMapper);
        }

        @Bean
        @ConditionalOnMissingBean
        CellGrpcService cellGrpcService(CellDataStore dataStore,
                                         ObjectMapper objectMapper) {
            log.info("Registering Honeycomb gRPC CellService (transport=grpc)");
            return new CellGrpcService(dataStore, objectMapper);
        }
    }

    @Configuration
    @ConditionalOnProperty(
            name = "honeycomb.grpc.transport",
            havingValue = "both",
            matchIfMissing = true
    )
    static class GrpcBothServerConfig {

        @Bean
        @ConditionalOnMissingBean
        SharedwallGrpcService sharedwallGrpcService(SharedwallMethodCache methodCache,
                                                     ObjectMapper objectMapper) {
            log.info("Registering Honeycomb gRPC SharedwallService (transport=both)");
            return new SharedwallGrpcService(methodCache, objectMapper);
        }

        @Bean
        @ConditionalOnMissingBean
        CellGrpcService cellGrpcService(CellDataStore dataStore,
                                         ObjectMapper objectMapper) {
            log.info("Registering Honeycomb gRPC CellService (transport=both)");
            return new CellGrpcService(dataStore, objectMapper);
        }
    }

    // ──────────────────── Health Service ────────────────────

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "honeycomb.grpc.health-enabled", havingValue = "true", matchIfMissing = true)
    HealthGrpcService healthGrpcService(HealthEndpoint healthEndpoint) {
        log.info("Registering Honeycomb gRPC HealthService");
        return new HealthGrpcService(healthEndpoint);
    }

    // ──────────────────── Server Interceptor ────────────────────

    @GrpcGlobalServerInterceptor
    HoneycombGrpcServerInterceptor honeycombGrpcServerInterceptor() {
        return new HoneycombGrpcServerInterceptor();
    }

    // ──────────────────── Client-Side Beans ────────────────────

    @Bean
    @ConditionalOnMissingBean
    HoneycombGrpcClientInterceptor honeycombGrpcClientInterceptor(HoneycombGrpcProperties props) {
        // fromCell is typically set per-client, but we provide a default interceptor
        return new HoneycombGrpcClientInterceptor(null);
    }

    @Bean
    @ConditionalOnMissingBean
    GrpcSharedwallClient grpcSharedwallClient(HoneycombGrpcProperties props) {
        var client = props.getClient();
        log.info("Creating default GrpcSharedwallClient targeting {}", client.getDefaultTarget());
        return GrpcSharedwallClient.builder()
                .target(client.getDefaultTarget())
                .deadline(client.getDeadline())
                .negotiationType(client.getNegotiationType())
                .loadBalancingPolicy(client.getLoadBalancingPolicy())
                .build();
    }
}
