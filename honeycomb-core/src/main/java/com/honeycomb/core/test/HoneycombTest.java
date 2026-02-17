package com.honeycomb.core.test;

import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.*;

/**
 * Composite test annotation that boots a minimal Honeycomb context
 * with in-memory storage, disabled security, and no external dependencies.
 *
 * <p>Use this for fast integration tests that need the full Honeycomb wiring
 * (cell registry, shared method cache, dispatching) without starting Redis,
 * Kafka, PostgreSQL, or Eureka.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @HoneycombTest
 * class MyCellIntegrationTest {
 *
 *     @Autowired
 *     WebTestClient webClient;
 *
 *     @Test
 *     void cellsAreDiscovered() {
 *         webClient.get().uri("/honeycomb/cells")
 *                 .exchange()
 *                 .expectStatus().isOk();
 *     }
 * }
 * }</pre>
 *
 * @since 1.4.3
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
        // in-memory storage (no Redis/Hibernate needed)
        "honeycomb.storage.type=memory",
        "honeycomb.idempotency.store=memory",

        // disable external services
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",

        // disable security for test simplicity
        "honeycomb.security.api-key.enabled=false",
        "honeycomb.security.jwt.enabled=false",
        "honeycomb.security.mtls.enabled=false",
        "spring.security.enabled=false",

        // disable features that need external infra
        "honeycomb.events.enabled=false",
        "honeycomb.locking.enabled=false",
        "honeycomb.tenant.enabled=false",
        "honeycomb.contracts.enabled=false",
        "honeycomb.versioning.enabled=false",

        // fast startup
        "honeycomb.shared.cache.warmup-enabled=true",
        "honeycomb.autoscale.enabled=false",

        // suppress noisy logging
        "logging.level.org.springframework.security=WARN",
        "logging.level.io.netty=WARN",

        // random port
        "server.port=0"
})
public @interface HoneycombTest {
}
