package com.honeycomb.core.service;

import com.honeycomb.core.config.HoneycombSharedMethodProperties;
import com.honeycomb.core.util.HoneycombConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Validates shared method request payloads against JSON Schema files.
 * <p>
 * Schemas are resolved from the classpath under {@code schemas/shared/} using
 * the method name and version as the key. For example, a method named
 * {@code echo} at version {@code v2} would look for
 * {@code schemas/shared/echo-v2.schema.json}.
 * <p>
 * Schema validation is controlled by the
 * {@code honeycomb.shared.methods.schema-validation-enabled} property.
 */
@Component
public class SharedMethodSchemaValidator {
    private static final Logger log = LoggerFactory.getLogger(SharedMethodSchemaValidator.class);

    private final HoneycombSharedMethodProperties properties;
    private final ResourceLoader resourceLoader;
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private final Map<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

    public SharedMethodSchemaValidator(HoneycombSharedMethodProperties properties,
                                      ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    /**
     * Validate the given JSON payload against the schema for the specified method and version.
     *
     * @return empty Mono if valid or schema not found (and failOnMissing is false),
     *         error Mono if validation fails.
     */
    public Mono<Void> validate(String methodName, String version, JsonNode payload) {
        if (!properties.isSchemaValidationEnabled()) {
            return Mono.empty();
        }
        if (payload == null || payload.isNull() || payload.isMissingNode()) {
            return Mono.empty();
        }
        String key = methodName + "-" + (version == null || version.isBlank() ? "v1" : version);
        return Mono.fromCallable(() -> {
            JsonSchema schema = schemaCache.computeIfAbsent(key, k -> loadSchema(k));
            if (schema == null) {
                return true; // no schema available
            }
            Set<ValidationMessage> errors = schema.validate(payload);
            if (!errors.isEmpty()) {
                String msg = errors.stream()
                        .map(ValidationMessage::getMessage)
                        .collect(Collectors.joining("; "));
                throw new IllegalArgumentException(
                        HoneycombConstants.Messages.SCHEMA_VALIDATION_FAILED + msg);
            }
            return true;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private JsonSchema loadSchema(String key) {
        String resourcePath = HoneycombConstants.Prefixes.CLASSPATH
                + "schemas/shared/" + key + ".schema.json";
        Resource resource = resourceLoader.getResource(resourcePath);
        if (!resource.exists()) {
            log.debug("No shared method schema found at {}", resourcePath);
            return null;
        }
        try (InputStream is = resource.getInputStream()) {
            log.info("Loaded shared method schema: {}", resourcePath);
            return schemaFactory.getSchema(is);
        } catch (Exception ex) {
            log.warn("Failed to load shared method schema {}: {}", resourcePath, ex.getMessage());
            return null;
        }
    }
}
