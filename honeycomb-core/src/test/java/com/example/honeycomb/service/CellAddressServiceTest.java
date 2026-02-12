package com.example.honeycomb.service;

import com.example.honeycomb.annotations.Cell;
import com.example.honeycomb.model.CellAddress;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.when;

public class CellAddressServiceTest {

    @Cell(port = 18081)
    static class SampleCell {}

    @Test
    void resolvesAddressesFromExplicitList() {
        CellRegistry registry = Mockito.mock(CellRegistry.class);
        ReactiveDiscoveryClient discoveryClient = Mockito.mock(ReactiveDiscoveryClient.class);
        when(registry.getCellNames()).thenReturn(Set.of("SampleCell"));
        when(registry.getCellClass("SampleCell")).thenReturn(Optional.of(SampleCell.class));
        when(discoveryClient.getInstances("SampleCell")).thenReturn(Flux.empty());

        MockEnvironment env = new MockEnvironment()
                .withProperty("cell.addresses.SampleCell", "svc-a:9001, svc-b:9002");

        CellAddressService s = new CellAddressService(registry, env, discoveryClient);
        ReflectionTestUtils.setField(s, "baseUrl", "http://ignored");

        StepVerifier.create(s.findByCell("SampleCell"))
                .recordWith(java.util.ArrayList::new)
                .expectNextCount(2)
                .consumeRecordedWith(list -> {
                    List<String> hosts = list.stream().map(CellAddress::getHost).toList();
                    List<Integer> ports = list.stream().map(CellAddress::getPort).toList();
                    org.assertj.core.api.Assertions.assertThat(hosts).containsExactlyInAnyOrder("svc-a", "svc-b");
                    org.assertj.core.api.Assertions.assertThat(ports).containsExactlyInAnyOrder(9001, 9002);
                })
                .verifyComplete();
    }

    @Test
    void fallsBackToAnnotatedPortAndBaseHost() {
        CellRegistry registry = Mockito.mock(CellRegistry.class);
        ReactiveDiscoveryClient discoveryClient = Mockito.mock(ReactiveDiscoveryClient.class);
        when(registry.getCellNames()).thenReturn(Set.of("SampleCell"));
        when(registry.getCellClass("SampleCell")).thenReturn(Optional.of(SampleCell.class));
        when(discoveryClient.getInstances("SampleCell")).thenReturn(Flux.empty());

        MockEnvironment env = new MockEnvironment();

        CellAddressService s = new CellAddressService(registry, env, discoveryClient);
        ReflectionTestUtils.setField(s, "baseUrl", "http://cell-host");

        StepVerifier.create(s.findByCell("SampleCell"))
                .assertNext(addr -> {
                    org.assertj.core.api.Assertions.assertThat(addr.getHost()).isEqualTo("cell-host");
                    org.assertj.core.api.Assertions.assertThat(addr.getPort()).isEqualTo(18081);
                })
                .verifyComplete();
    }

    @Test
    void usesDiscoveryInstancesWhenPresent() {
        CellRegistry registry = Mockito.mock(CellRegistry.class);
        ReactiveDiscoveryClient discoveryClient = Mockito.mock(ReactiveDiscoveryClient.class);
        when(registry.getCellNames()).thenReturn(Set.of("SampleCell"));
        when(registry.getCellClass("SampleCell")).thenReturn(Optional.of(SampleCell.class));

        DefaultServiceInstance instance = new DefaultServiceInstance("id-1", "SampleCell", "svc-discovery", 9123, false);
        when(discoveryClient.getInstances("SampleCell")).thenReturn(Flux.just(instance));

        MockEnvironment env = new MockEnvironment();
        CellAddressService s = new CellAddressService(registry, env, discoveryClient);

        StepVerifier.create(s.findByCell("SampleCell"))
                .assertNext(addr -> {
                    org.assertj.core.api.Assertions.assertThat(addr.getHost()).isEqualTo("svc-discovery");
                    org.assertj.core.api.Assertions.assertThat(addr.getPort()).isEqualTo(9123);
                })
                .verifyComplete();
    }
}
