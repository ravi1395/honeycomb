package com.example.honeycomb.health;

import com.example.honeycomb.service.DomainRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.test.StepVerifier;
import org.springframework.boot.actuate.health.Health;

import java.util.Set;
import java.util.Map;

import static org.mockito.Mockito.when;

public class DomainReactiveHealthIndicatorTest {

    @Test
    void healthIncludesDomainDetails() {
        DomainRegistry reg = Mockito.mock(DomainRegistry.class);
        when(reg.getDomainNames()).thenReturn(Set.of("A"));
        when(reg.describeDomain("A")).thenReturn(Map.of("className", "X"));

        DomainReactiveHealthIndicator h = new DomainReactiveHealthIndicator(reg);
        StepVerifier.create(h.health()).assertNext(health -> {
            assert health.getStatus().equals(org.springframework.boot.actuate.health.Status.UP);
            assert ((Map)health.getDetails().get("domains")).containsKey("A");
            assert ((Integer)health.getDetails().get("domainCount")) == 1;
        }).verifyComplete();
    }
}
