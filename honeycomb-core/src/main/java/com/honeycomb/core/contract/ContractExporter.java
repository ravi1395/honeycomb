package com.honeycomb.core.contract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.honeycomb.core.config.HoneycombContractProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Serialises {@link SharedMethodContract} instances into
 * Spring Cloud Contract YAML or Pact JSON format.
 *
 * <p>Output can be written to the configured {@code honeycomb.contracts.outputDir}
 * or served via the {@link ContractController} REST endpoint.</p>
 *
 * @since 1.4.3
 */
@Component
@ConditionalOnProperty(name = "honeycomb.contracts.enabled", havingValue = "true")
public class ContractExporter {
    private static final Logger log = LoggerFactory.getLogger(ContractExporter.class);

    private final HoneycombContractProperties properties;
    private final ObjectMapper objectMapper;

    public ContractExporter(HoneycombContractProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Export a list of contracts in the configured format.
     *
     * @return map of filename → content string
     */
    public Map<String, String> export(List<SharedMethodContract> contracts) {
        String fmt = properties.getFormat();
        if ("pact".equalsIgnoreCase(fmt)) {
            return exportPact(contracts);
        }
        return exportSpringCloudContract(contracts);
    }

    // -- Spring Cloud Contract YAML -----------------------------------------

    private Map<String, String> exportSpringCloudContract(List<SharedMethodContract> contracts) {
        Map<String, String> files = new LinkedHashMap<>();
        for (SharedMethodContract c : contracts) {
            String filename = c.methodName() + "_" + c.version() + ".yml";
            files.put(filename, toSccYaml(c));
        }
        return files;
    }

    private String toSccYaml(SharedMethodContract c) {
        StringBuilder sb = new StringBuilder();
        sb.append("description: \"Contract for shared method ").append(c.methodName())
                .append(" ").append(c.version()).append("\"\n");
        sb.append("name: \"").append(c.methodName()).append("_").append(c.version()).append("\"\n");
        sb.append("request:\n");
        sb.append("  method: POST\n");
        sb.append("  url: /honeycomb/shared/").append(c.methodName()).append("\n");
        sb.append("  headers:\n");
        sb.append("    Content-Type: application/json\n");
        sb.append("    X-Shared-Version: ").append(c.version()).append("\n");
        if (!c.exampleRequests().isEmpty()) {
            sb.append("  body:\n");
            try {
                String json = objectMapper.writeValueAsString(c.exampleRequests().getFirst());
                sb.append("    ").append(json).append("\n");
            } catch (JsonProcessingException ignored) { }
        }
        sb.append("response:\n");
        sb.append("  status: 200\n");
        sb.append("  headers:\n");
        sb.append("    Content-Type: application/json\n");
        if (!c.exampleResponses().isEmpty()) {
            sb.append("  body:\n");
            try {
                String json = objectMapper.writeValueAsString(c.exampleResponses().getFirst());
                sb.append("    ").append(json).append("\n");
            } catch (JsonProcessingException ignored) { }
        }
        return sb.toString();
    }

    // -- Pact JSON ----------------------------------------------------------

    private Map<String, String> exportPact(List<SharedMethodContract> contracts) {
        Map<String, String> files = new LinkedHashMap<>();
        Map<String, Object> pact = new LinkedHashMap<>();
        pact.put("provider", Map.of("name", "honeycomb"));
        pact.put("consumer", Map.of("name", "honeycomb-consumer"));

        List<Map<String, Object>> interactions = new ArrayList<>();
        for (SharedMethodContract c : contracts) {
            Map<String, Object> interaction = new LinkedHashMap<>();
            interaction.put("description", "Invoke shared method " + c.methodName() + " " + c.version());

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("method", "POST");
            request.put("path", "/honeycomb/shared/" + c.methodName());
            request.put("headers", Map.of(
                    "Content-Type", "application/json",
                    "X-Shared-Version", c.version()
            ));
            if (!c.exampleRequests().isEmpty()) {
                request.put("body", c.exampleRequests().getFirst());
            }
            interaction.put("request", request);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", 200);
            response.put("headers", Map.of("Content-Type", "application/json"));
            if (!c.exampleResponses().isEmpty()) {
                response.put("body", c.exampleResponses().getFirst());
            }
            interaction.put("response", response);

            interactions.add(interaction);
        }
        pact.put("interactions", interactions);
        pact.put("metadata", Map.of("pactSpecification", Map.of("version", "2.0.0")));

        try {
            files.put("honeycomb-pact.json", objectMapper.writeValueAsString(pact));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialise Pact JSON", e);
        }
        return files;
    }
}
