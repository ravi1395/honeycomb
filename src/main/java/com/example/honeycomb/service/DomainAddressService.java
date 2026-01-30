package com.example.honeycomb.service;

import com.example.honeycomb.model.DomainAddress;
import com.example.honeycomb.repo.DomainAddressRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class DomainAddressService {
    private final DomainAddressRepository repo;

    public DomainAddressService(DomainAddressRepository repo) {
        this.repo = repo;
    }

    public Flux<DomainAddress> listAll() { return repo.findAll(); }
    public Mono<DomainAddress> get(Long id) { return repo.findById(id); }
    public Flux<DomainAddress> findByDomain(String name) { return repo.findByDomainName(name); }
    public Mono<DomainAddress> create(DomainAddress a) { return repo.save(a); }
    public Mono<DomainAddress> update(Long id, DomainAddress a) {
        return repo.findById(id).flatMap(existing -> {
            existing.setDomainName(a.getDomainName());
            existing.setHost(a.getHost());
            existing.setPort(a.getPort());
            return repo.save(existing);
        });
    }
    public Mono<Void> delete(Long id) { return repo.deleteById(id); }
}
