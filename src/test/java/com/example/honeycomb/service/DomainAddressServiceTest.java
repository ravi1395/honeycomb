package com.example.honeycomb.service;

import com.example.honeycomb.model.DomainAddress;
import com.example.honeycomb.repo.DomainAddressRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class DomainAddressServiceTest {

    @Test
    void basicProxyMethods() {
        DomainAddressRepository repo = Mockito.mock(DomainAddressRepository.class);
        DomainAddress a = new DomainAddress(1L, "S", "host", 1234);
        when(repo.findAll()).thenReturn(Flux.fromIterable(List.of(a)));
        when(repo.findById(1L)).thenReturn(Mono.just(a));
        when(repo.findByDomainName("S")).thenReturn(Flux.fromIterable(List.of(a)));
        when(repo.save(any())).thenReturn(Mono.just(a));
        when(repo.deleteById(1L)).thenReturn(Mono.empty());

        DomainAddressService s = new DomainAddressService(repo);

        StepVerifier.create(s.listAll()).expectNextCount(1).verifyComplete();
        StepVerifier.create(s.get(1L)).expectNext(a).verifyComplete();
        StepVerifier.create(s.findByDomain("S")).expectNextCount(1).verifyComplete();
        StepVerifier.create(s.create(a)).expectNext(a).verifyComplete();
        StepVerifier.create(s.update(1L, a)).expectNext(a).verifyComplete();
        StepVerifier.create(s.delete(1L)).verifyComplete();
    }
}
