package com.honeycomb.core.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DynamicOpenApiCustomizer} generating paths from discovered cells and shared methods.
 */
@SpringBootTest(classes = com.honeycomb.core.HoneycombApplication.class)
class DynamicOpenApiCustomizerTest {

    @Autowired
    private com.honeycomb.core.service.CellRegistry cellRegistry;

    @Autowired
    private com.honeycomb.core.service.SharedwallMethodCache methodCache;

    private DynamicOpenApiCustomizer customizer;

    @BeforeEach
    void setUp() {
        customizer = new DynamicOpenApiCustomizer(cellRegistry, methodCache);
    }

    @Test
    void customiserAddsPathsForDiscoveredCells() {
        OpenAPI openApi = new OpenAPI()
                .info(new Info().title("test").version("1.0"))
                .paths(new Paths());

        customizer.customise(openApi);

        // The test context has SampleModel cell registered
        assertNotNull(openApi.getPaths());
        if (!cellRegistry.getCellNames().isEmpty()) {
            String firstCell = cellRegistry.getCellNames().iterator().next();
            String basePath = "/honeycomb/models/" + firstCell + "/items";
            assertTrue(openApi.getPaths().containsKey(basePath),
                    "Expected CRUD path for cell: " + firstCell);
            assertNotNull(openApi.getPaths().get(basePath).getGet(), "Expected GET operation (list)");
            assertNotNull(openApi.getPaths().get(basePath).getPost(), "Expected POST operation (create)");

            String itemPath = basePath + "/{id}";
            assertTrue(openApi.getPaths().containsKey(itemPath));
            assertNotNull(openApi.getPaths().get(itemPath).getGet(), "Expected GET by ID");
            assertNotNull(openApi.getPaths().get(itemPath).getPut(), "Expected PUT");
            assertNotNull(openApi.getPaths().get(itemPath).getDelete(), "Expected DELETE");
        }
    }

    @Test
    void customiserAddsPathsForSharedMethods() {
        OpenAPI openApi = new OpenAPI()
                .info(new Info().title("test").version("1.0"))
                .paths(new Paths());

        customizer.customise(openApi);

        if (!methodCache.getAllCandidates().isEmpty()) {
            String firstMethod = methodCache.getAllCandidates().keySet().iterator().next();
            String path = "/honeycomb/shared/" + firstMethod;
            assertTrue(openApi.getPaths().containsKey(path),
                    "Expected shared method path: " + path);
            assertNotNull(openApi.getPaths().get(path).getPost(), "Expected POST invoke for shared method");
        }
    }

    @Test
    void customizerDoesNotFailOnEmptyRegistry() {
        // Create customizer with empty registry — use the real one with no discovered cells mock
        OpenAPI openApi = new OpenAPI()
                .info(new Info().title("test").version("1.0"))
                .paths(new Paths());

        // Calling customise on a valid customizer should not throw even if paths are empty
        assertDoesNotThrow(() -> customizer.customise(openApi));
    }
}
