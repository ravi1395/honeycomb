package com.example.honeycomb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "honeycomb")
public class HoneycombProperties {

    /**
     * Map of cell name -> list of disabled operations (create,read,update,delete).
     * Use "*" as key for global defaults.
     */
    private Map<String, List<String>> disabledOperations = new HashMap<>();

    public Map<String, List<String>> getDisabledOperations() {
        return disabledOperations;
    }

    public void setDisabledOperations(Map<String, List<String>> disabledOperations) {
        this.disabledOperations = disabledOperations;
    }

    public boolean isOperationAllowed(String cell, String op) {
        if (cell == null) cell = "";
        String key = cell;
        // check cell-specific
        List<String> dis = disabledOperations.get(key);
        if (dis != null && dis.stream().anyMatch(s -> s.equalsIgnoreCase(op))) return false;
        // check global (support several possible keys for compatibility)
        List<String> global = disabledOperations.get("*");
        if (global == null) global = disabledOperations.get("__all__");
        if (global == null) global = disabledOperations.get("ALL");
        if (global == null) global = disabledOperations.get("0");
        if (global != null && global.stream().anyMatch(s -> s.equalsIgnoreCase(op))) return false;
        return true;
    }
}
