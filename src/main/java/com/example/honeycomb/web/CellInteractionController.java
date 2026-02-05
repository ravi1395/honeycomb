package com.example.honeycomb.web;

import com.example.honeycomb.service.AuditLogService;
import com.example.honeycomb.service.CellAddressService;
import com.example.honeycomb.service.RoutingPolicyService;
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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import com.example.honeycomb.util.HoneycombConstants;

@RestController
@RequestMapping(HoneycombConstants.Paths.CELLS_BASE)
@SuppressWarnings("null")
public class CellInteractionController {
    private final CellAddressService addressService;
    private final WebClient webClient;
    private final RoutingPolicyService routingPolicyService;
    private final AuditLogService auditLogService;

    public CellInteractionController(CellAddressService addressService,
                                     WebClient.Builder webClientBuilder,
                                     RoutingPolicyService routingPolicyService,
                                     AuditLogService auditLogService) {
        this.addressService = addressService;
        this.webClient = webClientBuilder.build();
        this.routingPolicyService = routingPolicyService;
        this.auditLogService = auditLogService;
    }

    /**
     * Invoke a named shared method on all instances of the target cell.
     * This expects target cells to expose an HTTP endpoint at `/honeycomb/shared/{method}`
     * which accepts POST bodies. Responses from each instance are aggregated.
     */
    @PostMapping(path = HoneycombConstants.Paths.CELLS_INVOKE_SHARED)
    public Mono<ResponseEntity<Map<String,Object>>> invokeShared(
            @PathVariable String from,
            @PathVariable String to,
            @PathVariable String methodName,
            @RequestParam(value = HoneycombConstants.Params.POLICY, required = false) String policy,
            @RequestHeader MultiValueMap<String, String> headers,
            @RequestBody(required = false) Mono<byte[]> bodyMono,
            ServerWebExchange exchange
    ) {
        final String pathFinal = HoneycombConstants.Paths.HONEYCOMB_SHARED
            + HoneycombConstants.Names.SEPARATOR_SLASH
            + methodName;

        return addressService.findByCell(to).collectList().flatMapMany(list -> {
            var selected = routingPolicyService.selectTargets(to, list, policy);
            if (selected.isEmpty()) {
                auditLogService.record(from, HoneycombConstants.Audit.ACTION_CELL_INVOKE, to, HoneycombConstants.Status.NO_TARGETS, Map.of(HoneycombConstants.JsonKeys.METHOD, methodName));
            }
            return Flux.fromIterable(selected);
        }).flatMap(addr -> {
                String base = HoneycombConstants.Schemes.HTTP
                    + addr.getHost()
                    + HoneycombConstants.Names.SEPARATOR_COLON
                    + addr.getPort();
            URI uri = Objects.requireNonNull(URI.create(base + pathFinal));

            WebClient.RequestBodySpec reqSpec = webClient.method(HttpMethod.POST).uri(uri)
                    .headers(h -> {
                        headers.forEach((k, v) -> {
                            if (k.equalsIgnoreCase(HttpHeaders.HOST)) return;
                            h.put(k, v);
                        });
                    });

            Mono<ClientResponse> respMono;
            Mono<byte[]> body = bodyMono != null ? bodyMono : Mono.just(new byte[0]);
                final MediaType contentType = exchange.getRequest().getHeaders().getContentType() == null
                    ? MediaType.APPLICATION_JSON
                    : exchange.getRequest().getHeaders().getContentType();
            respMono = body.flatMap(b -> reqSpec.contentType(contentType)
                    .bodyValue(b)
                    .exchangeToMono(Mono::just));

            return respMono.timeout(Duration.ofSeconds(10))
                        .flatMap(cr -> cr.bodyToMono(String.class).defaultIfEmpty(HoneycombConstants.Messages.EMPTY)
                            .map(bodyStr -> new AbstractMap.SimpleEntry<>(addr.getHost()
                                + HoneycombConstants.Names.SEPARATOR_COLON
                                + addr.getPort(), Map.of(
                            HoneycombConstants.JsonKeys.STATUS, cr.statusCode().value(),
                            HoneycombConstants.JsonKeys.CONTENT_TYPE,
                            cr.headers().contentType().map(MediaType::toString).orElse(HoneycombConstants.Messages.EMPTY),
                            HoneycombConstants.JsonKeys.BODY, bodyStr
                            ))))
                        .onErrorResume(e -> Mono.just(new AbstractMap.SimpleEntry<>(addr.getHost()
                            + HoneycombConstants.Names.SEPARATOR_COLON
                            + addr.getPort(), Map.of(HoneycombConstants.JsonKeys.ERROR, e.getMessage()))));
        }).collectList().map(list -> {
            Map<String,Object> aggregated = list.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                auditLogService.record(from, HoneycombConstants.Audit.ACTION_CELL_INVOKE, to, HoneycombConstants.Status.OK, Map.of(HoneycombConstants.JsonKeys.METHOD, methodName, HoneycombConstants.JsonKeys.TARGETS, aggregated.keySet()));
            return ResponseEntity.ok(aggregated);
        });
    }

    /**
     * Forward a request to all addresses for the target cell.
     * Supports specifying `method` and `path` as query parameters. If not provided,
     * defaults to POST and `/honeycomb/models/{to}/items`.
     * Headers from the incoming request are forwarded (except `host`).
     */
        @RequestMapping(path = HoneycombConstants.Paths.CELLS_FORWARD,
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public Mono<ResponseEntity<Map<String,Object>>> forward(
            @PathVariable String from,
            @PathVariable String to,
            @RequestParam(value = HoneycombConstants.Params.METHOD, required = false) String methodParam,
            @RequestParam(value = HoneycombConstants.Params.PATH, required = false) String pathParam,
            @RequestParam(value = HoneycombConstants.Params.POLICY, required = false) String policy,
            @RequestHeader MultiValueMap<String, String> headers,
            @RequestBody(required = false) Mono<byte[]> bodyMono,
            ServerWebExchange exchange
    ) {
        HttpMethod incomingMethod = exchange.getRequest().getMethod();
        final String methodFinal = (methodParam != null ? methodParam.toUpperCase()
            : (incomingMethod == null ? HoneycombConstants.HttpMethods.POST : incomingMethod.name()));
        final String pathFinal = (pathParam != null ? pathParam : HoneycombConstants.Paths.HONEYCOMB_MODELS
            + HoneycombConstants.Names.SEPARATOR_SLASH
            + to
            + HoneycombConstants.Names.SEPARATOR_SLASH
            + HoneycombConstants.Paths.ITEMS);

        return addressService.findByCell(to).collectList().flatMapMany(list -> {
            var selected = routingPolicyService.selectTargets(to, list, policy);
            if (selected.isEmpty()) {
                auditLogService.record(from, HoneycombConstants.Audit.ACTION_CELL_FORWARD, to, HoneycombConstants.Status.NO_TARGETS, Map.of(HoneycombConstants.JsonKeys.PATH, pathFinal, HoneycombConstants.JsonKeys.METHOD, methodFinal));
            }
            return Flux.fromIterable(selected);
        }).flatMap(addr -> {
                String base = HoneycombConstants.Schemes.HTTP
                    + addr.getHost()
                    + HoneycombConstants.Names.SEPARATOR_COLON
                    + addr.getPort();
            URI uri = Objects.requireNonNull(URI.create(base + pathFinal));

            WebClient.RequestBodySpec reqSpec = webClient.method(HttpMethod.valueOf(methodFinal)).uri(uri)
                    .headers(h -> {
                        headers.forEach((k, v) -> {
                            if (k.equalsIgnoreCase(HttpHeaders.HOST)) return;
                            h.put(k, v);
                        });
                    });

            Mono<ClientResponse> respMono;
            if (methodFinal.equals(HoneycombConstants.HttpMethods.GET) || methodFinal.equals(HoneycombConstants.HttpMethods.DELETE)) {
                respMono = reqSpec.accept(MediaType.APPLICATION_JSON, MediaType.ALL).exchangeToMono(Mono::just);
            } else {
                Mono<byte[]> body = bodyMono != null ? bodyMono : Mono.just(new byte[0]);
                final MediaType contentType = exchange.getRequest().getHeaders().getContentType() == null
                    ? MediaType.APPLICATION_JSON
                    : exchange.getRequest().getHeaders().getContentType();
                respMono = body.flatMap(b -> reqSpec.contentType(contentType)
                        .bodyValue(b)
                        .exchangeToMono(Mono::just));
            }

            return respMono.timeout(Duration.ofSeconds(10))
                    .flatMap(cr -> cr.bodyToMono(String.class).defaultIfEmpty(HoneycombConstants.Messages.EMPTY)
                        .map(bodyStr -> new AbstractMap.SimpleEntry<>(addr.getHost()
                            + HoneycombConstants.Names.SEPARATOR_COLON
                            + addr.getPort(), Map.of(
                            HoneycombConstants.JsonKeys.STATUS, cr.statusCode().value(),
                        HoneycombConstants.JsonKeys.CONTENT_TYPE,
                        cr.headers().contentType().map(MediaType::toString).orElse(HoneycombConstants.Messages.EMPTY),
                            HoneycombConstants.JsonKeys.BODY, bodyStr
                            ))))
                    .onErrorResume(e -> Mono.just(new AbstractMap.SimpleEntry<>(addr.getHost()
                        + HoneycombConstants.Names.SEPARATOR_COLON
                        + addr.getPort(), Map.of(HoneycombConstants.JsonKeys.ERROR, e.getMessage()))));
        }).collectList().map(list -> {
            Map<String,Object> aggregated = list.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                auditLogService.record(from, HoneycombConstants.Audit.ACTION_CELL_FORWARD, to, HoneycombConstants.Status.OK, Map.of(HoneycombConstants.JsonKeys.PATH, pathFinal, HoneycombConstants.JsonKeys.METHOD, methodFinal, HoneycombConstants.JsonKeys.TARGETS, aggregated.keySet()));
            return ResponseEntity.ok(aggregated);
        });
    }
}
