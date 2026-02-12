package com.example.honeycomb.service;

import com.example.honeycomb.config.HoneycombValidationProperties;
import com.example.honeycomb.util.HoneycombConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CellSchemaValidator {
    private final HoneycombValidationProperties properties;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    public CellSchemaValidator(HoneycombValidationProperties properties,
                               ObjectMapper objectMapper,
                               ResourceLoader resourceLoader) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    public Mono<Void> validate(String cell, Object payload) {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }
        String schemaFile = schemaFileFor(cell);
        String resourcePath = HoneycombConstants.Prefixes.CLASSPATH
            + normalizeDir(properties.getSchemaDir())
            + "/"
            + schemaFile;
        Resource resource = resourceLoader.getResource(resourcePath);
        if (!resource.exists()) {
            if (properties.isFailOnMissingSchema()) {
                return Mono.error(new IllegalArgumentException(HoneycombConstants.Messages.SCHEMA_MISSING + schemaFile));
            }
            return Mono.empty();
        }
        return Mono.fromCallable(() -> {
            try (InputStream is = resource.getInputStream()) {
                JsonSchema schema = schemaFactory.getSchema(is);
                JsonNode node = objectMapper.valueToTree(payload);
                Set<ValidationMessage> errors = schema.validate(node);
                if (!errors.isEmpty()) {
                    String msg = errors.stream()
                            .map(ValidationMessage::getMessage)
                            .collect(Collectors.joining(HoneycombConstants.Names.LIST_SEPARATOR));
                    throw new IllegalArgumentException(HoneycombConstants.Messages.SCHEMA_VALIDATION_FAILED + msg);
                }
                return true;
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private String schemaFileFor(String cell) {
        if (properties.getPerCell() != null) {
            String override = properties.getPerCell().get(cell);
            if (override != null && !override.isBlank()) {
                return override;
            }
        }
        return cell + HoneycombConstants.Suffixes.SCHEMA_JSON;
    }

    private String normalizeDir(String dir) {
        if (dir == null || dir.isBlank()) {
            return HoneycombConstants.Defaults.SCHEMAS_DIR;
        }
        return dir.replaceAll(HoneycombConstants.Regex.LEADING_SLASHES, "")
            .replaceAll(HoneycombConstants.Regex.TRAILING_SLASHES, "");
    }
}
