package com.example.honeycomb.health;

import com.example.honeycomb.service.CellRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.when;

public class CellReactiveHealthIndicatorTest {

    @Test
    void healthIncludesCellDetails() {
        CellRegistry reg = Mockito.mock(CellRegistry.class);
        when(reg.getCellNames()).thenReturn(Set.of("A"));
        when(reg.describeCell("A")).thenReturn(Map.of("className", "X"));

        CellReactiveHealthIndicator h = new CellReactiveHealthIndicator(reg);
        StepVerifier.create(h.health()).assertNext(health -> {
            assert health.getStatus().equals(org.springframework.boot.actuate.health.Status.UP);
            assert ((Map)health.getDetails().get("cells")).containsKey("A");
            assert ((Integer)health.getDetails().get("cellCount")) == 1;
        }).verifyComplete();
    }
}
