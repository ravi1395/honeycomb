package com.example.honeycomb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = com.example.honeycomb.util.HoneycombConstants.ConfigKeys.VALIDATION_PREFIX,
    ignoreInvalidFields = true)
public class HoneycombValidationProperties {
    /**
     * Enable JSON schema validation for cell create/update payloads.
     */
    private boolean enabled = false;

    /**
     * Classpath directory that contains JSON schemas.
     */
    private String schemaDir = com.example.honeycomb.util.HoneycombConstants.Defaults.SCHEMAS_DIR;

    /**
     * Map of cell name -> schema file name.
     */
    private Map<String, String> perCell = new HashMap<>();

    /**
     * If true, reject requests when schema is missing.
     */
    private boolean failOnMissingSchema = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSchemaDir() {
        return schemaDir;
    }

    public void setSchemaDir(String schemaDir) {
        this.schemaDir = schemaDir;
    }

    public Map<String, String> getPerCell() {
        return perCell;
    }

    public void setPerCell(Map<String, String> perCell) {
        this.perCell = perCell;
    }

    public boolean isFailOnMissingSchema() {
        return failOnMissingSchema;
    }

    public void setFailOnMissingSchema(boolean failOnMissingSchema) {
        this.failOnMissingSchema = failOnMissingSchema;
    }
}
