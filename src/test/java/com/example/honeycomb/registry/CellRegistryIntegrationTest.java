package com.example.honeycomb.registry;

import com.example.honeycomb.annotations.Cell;
import com.example.honeycomb.annotations.Sharedwall;
import com.example.honeycomb.service.CellRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;

import java.util.Map;

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
        assertTrue(((java.util.List)desc.get("sharedMethods")).contains("ping"));
    }
}
