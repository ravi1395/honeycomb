package com.honeycomb.core.aot;

import com.honeycomb.core.annotations.Cell;
import com.honeycomb.core.annotations.MethodType;
import com.honeycomb.core.annotations.Sharedwall;
import com.honeycomb.core.dto.*;
import com.honeycomb.core.model.CellAddress;
import com.honeycomb.core.persistence.CellRecord;
import org.springframework.aot.hint.*;

import java.util.Set;

/**
 * Registers GraalVM native-image runtime hints for the Honeycomb framework.
 *
 * <p>Ensures that reflection, serialization, proxy, and resource hints are
 * available so that {@code @Cell}, {@code @Sharedwall}, and related DTOs work
 * correctly when compiled to a GraalVM native image.</p>
 *
 * <p>Registered automatically via
 * {@link org.springframework.context.annotation.ImportRuntimeHints} on
 * {@link com.honeycomb.core.aot.HoneycombAotConfiguration}.</p>
 *
 * @since 1.4.2
 * @see org.springframework.aot.hint.RuntimeHintsRegistrar
 */
public class HoneycombRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * Core framework classes that need full reflection access at runtime
     * (constructor, fields, methods) for JSON serialization, Spring DI,
     * and method invocation via {@code LambdaMetafactory} / reflection.
     */
    private static final Set<Class<?>> REFLECTION_TYPES = Set.of(
            // Annotations
            Cell.class,
            Sharedwall.class,
            MethodType.class,
            // Model / Persistence
            CellAddress.class,
            CellRecord.class,
            // DTOs
            SharedwallInvokeInfo.class,
            SharedMethodInfo.class,
            SharedMethodInfo.ParameterInfo.class,
            CellInfo.class,
            CellRuntimeStatus.class,
            CellEvent.class,
            AuditEvent.class,
            ErrorResponse.class,
            BatchInvokeRequest.class,
            BatchInvokeResponse.class
    );

    /**
     * Classpath resources that must be reachable in the native image.
     */
    private static final Set<String> RESOURCE_PATTERNS = Set.of(
            "application.yml",
            "application.properties",
            "application-prod.yml",
            "logback-spring.xml",
            "schemas/*"
    );

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        registerReflectionHints(hints.reflection());
        registerSerializationHints(hints.serialization());
        registerResourceHints(hints.resources());
        registerProxyHints(hints.proxies());
    }

    /* ------------------------------------------------------------------ */
    /*  Reflection                                                         */
    /* ------------------------------------------------------------------ */

    private void registerReflectionHints(ReflectionHints reflection) {
        for (Class<?> type : REFLECTION_TYPES) {
            reflection.registerType(type, MemberCategory.values());
        }

        // The MethodOp enum used by @MethodType
        reflection.registerType(
                com.honeycomb.core.annotations.MethodOp.class,
                MemberCategory.values()
        );
    }

    /* ------------------------------------------------------------------ */
    /*  Serialization                                                      */
    /* ------------------------------------------------------------------ */

    private void registerSerializationHints(SerializationHints serialization) {
        for (Class<?> type : REFLECTION_TYPES) {
            if (java.io.Serializable.class.isAssignableFrom(type)) {
                @SuppressWarnings("unchecked")
                Class<? extends java.io.Serializable> serializableType =
                        (Class<? extends java.io.Serializable>) type;
                serialization.registerType(serializableType);
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Resources                                                          */
    /* ------------------------------------------------------------------ */

    private void registerResourceHints(ResourceHints resources) {
        for (String pattern : RESOURCE_PATTERNS) {
            resources.registerPattern(pattern);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  JDK Proxies                                                        */
    /* ------------------------------------------------------------------ */

    private void registerProxyHints(ProxyHints proxies) {
        // CellDataStore and IdempotencyStore are programmatically proxied
        // when conditional beans are used
        proxies.registerJdkProxy(
                com.honeycomb.core.service.CellDataStore.class,
                org.springframework.aop.SpringProxy.class,
                org.springframework.aop.framework.Advised.class,
                org.springframework.core.DecoratingProxy.class
        );
        proxies.registerJdkProxy(
                com.honeycomb.core.service.IdempotencyStore.class,
                org.springframework.aop.SpringProxy.class,
                org.springframework.aop.framework.Advised.class,
                org.springframework.core.DecoratingProxy.class
        );
    }
}
