package com.example.honeycomb.web;

import com.example.honeycomb.model.DomainAddress;
import com.example.honeycomb.service.DomainAddressService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.List;
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
     * Invoke a named shared method on all instances of the target cell.
     * This expects target cells to expose an HTTP endpoint at `/honeycomb/shared/{method}`
     * which accepts POST bodies. Responses from each instance are aggregated.
     */
    @PostMapping(path = "/{from}/invoke/{to}/shared/{methodName}")
    public Mono<ResponseEntity<Map<String,Object>>> invokeShared(
            @PathVariable String from,
            @PathVariable String to,
            @PathVariable String methodName,
            @RequestHeader MultiValueMap<String, String> headers,
            @RequestBody(required = false) Mono<byte[]> bodyMono,
            ServerWebExchange exchange
    ) {
        final String pathFinal = "/honeycomb/shared/" + methodName;

        Flux<DomainAddress> targets = addressService.findByDomain(to);

        return targets.flatMap(addr -> {
            String base = "http://" + addr.getHost() + ":" + addr.getPort();
            URI uri = URI.create(base + pathFinal);

            WebClient.RequestBodySpec reqSpec = webClient.method(HttpMethod.POST).uri(uri)
                    .headers(h -> {
                        headers.forEach((k, v) -> {
                            if (k.equalsIgnoreCase(HttpHeaders.HOST)) return;
                            h.put(k, v);
                        });
                    });

            Mono<ClientResponse> respMono;
            Mono<byte[]> body = bodyMono != null ? bodyMono : Mono.just(new byte[0]);
            respMono = body.flatMap(b -> reqSpec.contentType(exchange.getRequest().getHeaders().getContentType() == null ? MediaType.APPLICATION_JSON : exchange.getRequest().getHeaders().getContentType())
                    .bodyValue(b)
                    .exchangeToMono(Mono::just));

            return respMono.timeout(Duration.ofSeconds(10))
                    .flatMap(cr -> cr.bodyToMono(String.class).defaultIfEmpty("")
                            .map(bodyStr -> {
                                return new AbstractMap.SimpleEntry<>(addr.getHost() + ":" + addr.getPort(), Map.of(
                                        "status", cr.statusCode().value(),
                                        "contentType", cr.headers().contentType().map(MediaType::toString).orElse(""),
                                        "body", bodyStr
                                ));
                            }))
                    .onErrorResume(e -> Mono.just(new AbstractMap.SimpleEntry<>(addr.getHost() + ":" + addr.getPort(), Map.of("error", e.getMessage()))));
        }).collectList().map(list -> {
            Map<String,Object> aggregated = list.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            return ResponseEntity.ok(aggregated);
        });
    }

    /**
     * Forward a request to all addresses for the target domain.
     * Supports specifying `method` and `path` as query parameters. If not provided,
     * defaults to POST and `/honeycomb/models/{to}/items`.
     * Headers from the incoming request are forwarded (except `host`).
     */
    @RequestMapping(path = "/{from}/forward/{to}", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public Mono<ResponseEntity<Map<String,Object>>> forward(
            @PathVariable String from,
            @PathVariable String to,
            @RequestParam(value = "method", required = false) String methodParam,
            @RequestParam(value = "path", required = false) String pathParam,
            @RequestHeader MultiValueMap<String, String> headers,
            @RequestBody(required = false) Mono<byte[]> bodyMono,
            ServerWebExchange exchange
    ) {
        final String methodFinal = (methodParam != null ? methodParam.toUpperCase() : (exchange.getRequest().getMethodValue() == null ? "POST" : exchange.getRequest().getMethodValue()));
        final String pathFinal = (pathParam != null ? pathParam : "/honeycomb/models/" + to + "/items");

        Flux<DomainAddress> targets = addressService.findByDomain(to);

        return targets.flatMap(addr -> {
            String base = "http://" + addr.getHost() + ":" + addr.getPort();
                URI uri = URI.create(base + pathFinal);

                WebClient.RequestBodySpec reqSpec = webClient.method(HttpMethod.valueOf(methodFinal)).uri(uri)
                    .headers(h -> {
                        // copy headers except host
                        headers.forEach((k, v) -> {
                            if (k.equalsIgnoreCase(HttpHeaders.HOST)) return;
                            h.put(k, v);
                        });
                    });

            Mono<ClientResponse> respMono;
            if (methodFinal.equals("GET") || methodFinal.equals("DELETE")) {
                respMono = reqSpec.accept(MediaType.APPLICATION_JSON, MediaType.ALL).exchangeToMono(Mono::just);
            } else {
                Mono<byte[]> body = bodyMono != null ? bodyMono : Mono.just(new byte[0]);
                respMono = body.flatMap(b -> reqSpec.contentType(exchange.getRequest().getHeaders().getContentType() == null ? MediaType.APPLICATION_JSON : exchange.getRequest().getHeaders().getContentType())
                        .bodyValue(b)
                        .exchangeToMono(Mono::just));
            }

            return respMono.timeout(Duration.ofSeconds(10))
                    .flatMap(cr -> cr.bodyToMono(String.class).defaultIfEmpty("")
                            .map(bodyStr -> {
                                return new AbstractMap.SimpleEntry<>(addr.getHost() + ":" + addr.getPort(), Map.of(
                                        "status", cr.statusCode().value(),
                                        "contentType", cr.headers().contentType().map(MediaType::toString).orElse(""),
                                        "body", bodyStr
                                ));
                            }))
                    .onErrorResume(e -> Mono.just(new AbstractMap.SimpleEntry<>(addr.getHost() + ":" + addr.getPort(), Map.of("error", e.getMessage()))));
        }).collectList().map(list -> {
            Map<String,Object> aggregated = list.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            return ResponseEntity.ok(aggregated);
        });
    }
}
