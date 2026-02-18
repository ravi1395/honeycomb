package com.honeycomb.security;

import com.honeycomb.core.config.HoneycombSecurityProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot auto-configuration for the {@code honeycomb-security} module.
 *
 * <p>Activated automatically when {@code honeycomb-security} is on the classpath
 * (via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}).
 * Registers all security filters ({@link ApiKeyAuthFilter}, {@link MtlsAuthFilter},
 * {@link JwtCellAccessFilter}) and the reactive {@link SecurityConfig} bean.</p>
 *
 * <p>Production hardening defaults (require-auth, rate-limiting, strict headers)
 * are provided by {@code application-prod.yml} bundled in this module — they
 * activate automatically when the {@code prod} Spring profile is active.</p>
 *
 * <p>To opt <em>in</em> to security, add {@code honeycomb-security} to your
 * project's dependencies.  To disable individual features selectively, set
 * the corresponding properties (e.g. {@code honeycomb.security.api-keys.enabled=false}).</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(HoneycombSecurityProperties.class)
@ComponentScan(basePackages = "com.honeycomb.security")
public class HoneycombSecurityAutoConfiguration {
}
