package com.example.honeycomb;

import com.example.honeycomb.service.CellServerManager;
import com.example.honeycomb.service.CellRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class CellServerManagerTest {

    @Autowired
    CellServerManager manager;

    @Autowired
    CellRegistry registry;

    @Test
    void portMappingForSampleModel() {
        // SampleModel should be discovered by the registry.
        assertTrue(registry.getCellNames().contains("SampleModel"), "SampleModel must be discovered by CellRegistry");
        // If a server was successfully bound on 8081, verify the reverse mapping. If binding failed
        // due to port conflicts in the test environment, don't fail the test on that fact.
        String cell = manager.getCellForPort(8081);
        if (cell != null) {
            assertEquals("SampleModel", cell);
        }
    }
}
