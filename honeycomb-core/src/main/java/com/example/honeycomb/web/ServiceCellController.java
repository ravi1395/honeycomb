package com.example.honeycomb.web;

import com.example.honeycomb.annotations.MethodOp;
import com.example.honeycomb.service.ServiceCellRegistry;
import com.example.honeycomb.util.HoneycombConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.reactivestreams.Publisher;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.Map;

@RestController
@RequestMapping("/honeycomb/service")
@SuppressWarnings("null")
public class ServiceCellController {
    private final ServiceCellRegistry registry;
    private final ObjectMapper objectMapper;

    public ServiceCellController(ServiceCellRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @RequestMapping(path = {"/{cell}/{method}", "/{cell}/{method}/{id}"},
        method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public Mono<ResponseEntity<Object>> dispatch(
        @PathVariable String cell,
        @PathVariable String method,
        @PathVariable(required = false) String id,
        @RequestBody(required = false) Mono<byte[]> bodyMono,
        ServerWebExchange exchange
    ) {
        var svcMethod = registry.getMethod(cell, method);
        if (svcMethod == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(HoneycombConstants.JsonKeys.ERROR, "service-method-not-found")));
        }

        HttpMethod httpMethod = exchange.getRequest().getMethod();
        if (httpMethod == null || !isAllowed(svcMethod.getOp(), httpMethod)) {
            return Mono.just(ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(HoneycombConstants.JsonKeys.ERROR, "method-not-allowed")));
        }

        Mono<byte[]> body = bodyMono == null ? Mono.just(new byte[0]) : bodyMono.defaultIfEmpty(new byte[0]);

        return body.flatMap(bytes -> invoke(svcMethod.getBean(), svcMethod.getMethod(), id, bytes, exchange))
            .flatMap(this::adaptResult)
            .map(res -> {
                if (res instanceof ResponseEntity<?> re) {
                    @SuppressWarnings("unchecked")
                    ResponseEntity<Object> cast = (ResponseEntity<Object>) re;
                    return cast;
                }
                return ResponseEntity.ok(res);
            })
            .onErrorResume(ex -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(HoneycombConstants.JsonKeys.ERROR, ex.getMessage() == null ? "error" : ex.getMessage()))));
    }

    private boolean isAllowed(MethodOp op, HttpMethod method) {
        return switch (op) {
            case READ -> method == HttpMethod.GET;
            case CREATE -> method == HttpMethod.POST;
            case UPDATE -> method == HttpMethod.PUT || method == HttpMethod.PATCH;
            case DELETE -> method == HttpMethod.DELETE;
            case CUSTOM, SHARED -> method == HttpMethod.POST;
        };
    }

    private Mono<Object> invoke(Object bean, Method method, String id, byte[] body, ServerWebExchange exchange) {
        try {
            int paramCount = method.getParameterCount();
            if (paramCount == 0) {
                Object res = method.invoke(bean);
                return Mono.justOrEmpty(res);
            }
            if (paramCount == 1) {
                Class<?> paramType = method.getParameterTypes()[0];
                Object arg = convertArg(paramType, id, body, exchange);
                Object res = method.invoke(bean, arg);
                return Mono.justOrEmpty(res);
            }
            if (paramCount == 2) {
                Class<?>[] paramTypes = method.getParameterTypes();
                Object arg0 = convertArg(paramTypes[0], id, body, exchange);
                Object arg1 = convertBodyArg(paramTypes[1], body);
                Object res = method.invoke(bean, arg0, arg1);
                return Mono.justOrEmpty(res);
            }
            return Mono.error(new IllegalArgumentException("unsupported-params"));
        } catch (Throwable ex) {
            return Mono.error(ex);
        }
    }

    private Object convertArg(Class<?> paramType, String id, byte[] body, ServerWebExchange exchange) throws Exception {
        if (paramType.equals(byte[].class)) return body;
        if (paramType.equals(String.class)) {
            if (id != null && !id.isBlank()) return id;
            String payload = exchange.getRequest().getQueryParams().getFirst("payload");
            if (payload != null && !payload.isBlank()) return payload;
            return new String(body);
        }
        if (body == null || body.length == 0) return null;
        return objectMapper.readValue(body, paramType);
    }

    private Object convertBodyArg(Class<?> paramType, byte[] body) throws Exception {
        if (paramType.equals(byte[].class)) return body;
        if (paramType.equals(String.class)) return body == null ? null : new String(body);
        if (body == null || body.length == 0) return null;
        return objectMapper.readValue(body, paramType);
    }

    private Mono<Object> adaptResult(Object res) {
        if (res instanceof Mono<?> mono) {
            return mono.map(r -> (Object) r);
        }
        if (res instanceof Flux<?> flux) {
            return flux.collectList().map(r -> (Object) r);
        }
        if (res instanceof Publisher<?> pub) {
            return Flux.from(pub).collectList().map(r -> (Object) r);
        }
        return Mono.justOrEmpty(res);
    }
}
