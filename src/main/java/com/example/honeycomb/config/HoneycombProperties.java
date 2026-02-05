package com.example.honeycomb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.example.honeycomb.util.HoneycombConstants;

@ConfigurationProperties(prefix = HoneycombConstants.ConfigKeys.HONEYCOMB_PREFIX)
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
        if (cell == null) cell = HoneycombConstants.Messages.EMPTY;
        String key = cell;
        // check cell-specific
        List<String> dis = disabledOperations.get(key);
        if (dis != null && dis.stream().anyMatch(s -> s.equalsIgnoreCase(op))) return false;
        // check global (support several possible keys for compatibility)
        List<String> global = disabledOperations.get(HoneycombConstants.ConfigKeys.GLOBAL_WILDCARD);
        if (global == null) global = disabledOperations.get(HoneycombConstants.ConfigKeys.GLOBAL_ALL);
        if (global == null) global = disabledOperations.get(HoneycombConstants.ConfigKeys.GLOBAL_ALL_UPPER);
        if (global == null) global = disabledOperations.get(HoneycombConstants.ConfigKeys.GLOBAL_ZERO);
        if (global != null && global.stream().anyMatch(s -> s.equalsIgnoreCase(op))) return false;
        return true;
    }
}
