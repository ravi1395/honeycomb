package com.example.honeycomb.repo;

import com.example.honeycomb.model.DomainAddress;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface DomainAddressRepository extends ReactiveCrudRepository<DomainAddress, Long> {
    Flux<DomainAddress> findByDomainName(String domainName);
}
