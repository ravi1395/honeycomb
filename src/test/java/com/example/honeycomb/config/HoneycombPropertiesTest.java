package com.example.honeycomb.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class HoneycombPropertiesTest {

    @Test
    void operationAllowedChecks() {
        HoneycombProperties p = new HoneycombProperties();
        p.setDisabledOperations(Map.of(
                "Sample", List.of("create","update"),
                "__all__", List.of("delete")
        ));

        assertFalse(p.isOperationAllowed("Sample", "create"));
        assertFalse(p.isOperationAllowed("Sample", "update"));
        assertTrue(p.isOperationAllowed("Sample", "read"));

        assertFalse(p.isOperationAllowed("Other", "delete"));
        assertTrue(p.isOperationAllowed("Other", "create"));
    }
}
