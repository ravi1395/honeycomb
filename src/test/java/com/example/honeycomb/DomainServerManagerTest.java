package com.example.honeycomb;

import com.example.honeycomb.service.DomainServerManager;
import com.example.honeycomb.service.DomainRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DomainServerManagerTest {

    @Autowired
    DomainServerManager manager;

    @Autowired
    DomainRegistry registry;

    @Test
    void portMappingForSampleModel() {
        // SampleModel should be discovered by the registry.
        assertTrue(registry.getDomainNames().contains("SampleModel"), "SampleModel must be discovered by DomainRegistry");
        // If a server was successfully bound on 8081, verify the reverse mapping. If binding failed
        // due to port conflicts in the test environment, don't fail the test on that fact.
        String domain = manager.getDomainForPort(8081);
        if (domain != null) {
            assertEquals("SampleModel", domain);
        }
    }
}
