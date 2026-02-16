package com.honeycomb.core.aot;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Auto-configuration that activates GraalVM native-image AOT hints
 * for the Honeycomb framework.
 *
 * <ul>
 *   <li>{@link HoneycombRuntimeHints} — static hints for framework DTOs,
 *       annotations, resources, and JDK proxies</li>
 *   <li>{@link CellBeanAotProcessor} — build-time processor that discovers
 *       {@code @Cell}-annotated beans and registers reflection hints</li>
 * </ul>
 *
 * @since 1.4.2
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(HoneycombRuntimeHints.class)
public class HoneycombAotConfiguration {

    /**
     * Registers the {@link CellBeanAotProcessor} so that Spring AOT can
     * discover user-defined {@code @Cell} beans and emit native-image hints.
     */
    @Bean
    static CellBeanAotProcessor cellBeanAotProcessor() {
        return new CellBeanAotProcessor();
    }
}
