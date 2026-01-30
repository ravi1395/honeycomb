package com.example.honeycomb.web;

import com.example.honeycomb.model.DomainAddress;
import com.example.honeycomb.service.DomainAddressService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/domains")
public class DomainInteractionController {
    private final DomainAddressService addressService;
    private final WebClient webClient;

    public DomainInteractionController(DomainAddressService addressService, WebClient.Builder webClientBuilder) {
        this.addressService = addressService;
        this.webClient = webClientBuilder.build();
    }

    /**
     * Invoke target domain's default items endpoint and forward payload to each discovered address.
     * Aggregates responses keyed by host:port.
     */
    @PostMapping(path = "/{from}/invoke/{to}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String,Object>>> invoke(@PathVariable String from, @PathVariable String to, @RequestBody(required = false) Map<String,Object> body) {
        Flux<DomainAddress> targets = addressService.findByDomain(to);
        return targets.flatMap(addr -> {
            String base = "http://" + addr.getHost() + ":" + addr.getPort();
            // target path: /honeycomb/models/{to}/items
            URI uri = URI.create(base + "/honeycomb/models/" + to + "/items");
            return webClient.post().uri(uri).bodyValue(body == null ? Map.of() : body)
                    .retrieve().bodyToMono(Map.class)
                    .map(resp -> Map.entry(addr.getHost() + ":" + addr.getPort(), resp))
                    .onErrorResume(e -> Mono.just(Map.entry(addr.getHost() + ":" + addr.getPort(), Map.of("error", e.getMessage()))));
        }).collectList().map(list -> {
            Map<String,Object> aggregated = list.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            return ResponseEntity.ok(aggregated);
        });
    }
}
