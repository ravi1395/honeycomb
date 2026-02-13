package com.honeycomb.core.registry;

import com.honeycomb.core.annotations.Cell;
import com.honeycomb.core.annotations.Sharedwall;
import com.honeycomb.core.service.CellRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class CellRegistryIntegrationTest {

    @Autowired
    CellRegistry registry;

    // test component to be discovered
    @Cell(port = 0, value = "TestCell")
    @Component
    static class TestCellBean {
        public String foo;

        @Sharedwall("ping")
        public String ping(String s) { return "pong:"+s; }
    }

    @Test
    void registryFindsTestCell() {
        var names = registry.getCellNames();
        assertTrue(names.contains("TestCell"));
        Map<String,Object> desc = registry.describeCell("TestCell");
        assertEquals(TestCellBean.class.getName(), desc.get("className"));
        List<?> sharedMethods = (List<?>) Objects.requireNonNull(desc.get("sharedMethods"), "sharedMethods");
        assertTrue(sharedMethods.contains("ping"));
    }
}
