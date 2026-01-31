package com.example.honeycomb.web;

import com.example.honeycomb.annotations.Cell;
import com.example.honeycomb.annotations.Sharedwall;
import com.example.honeycomb.service.DomainRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/honeycomb/shared")
public class SharedwallDispatcherController {
    private final ApplicationContext ctx;
    private final DomainRegistry registry;
    private final ObjectMapper objectMapper;

    public SharedwallDispatcherController(ApplicationContext ctx, DomainRegistry registry, ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    /**
     * Generic entrypoint that invokes local methods marked with `@Sharedwall`.
     * It looks up beans annotated with `@Cell` and finds methods with matching name or alias.
     * Supported method signatures: () , (String) , (byte[]).
     */
    @PostMapping("/{methodName}")
    public Mono<ResponseEntity<Map<String,Object>>> dispatch(
            @PathVariable String methodName,
            @RequestHeader MultiValueMap<String, String> headers,
            @RequestBody(required = false) Mono<byte[]> bodyMono
    ) {
        // discover local candidates
        List<MethodCandidate> candidates = new ArrayList<>();
        for (String beanName : ctx.getBeanDefinitionNames()) {
            try {
                Object bean = ctx.getBean(beanName);
                Class<?> cls = bean.getClass();
                if (!cls.isAnnotationPresent(Cell.class)) continue;
                for (Method m : cls.getDeclaredMethods()) {
                    Sharedwall s = m.getAnnotation(Sharedwall.class);
                    if (s == null) continue;
                    String alias = (s.value() != null && !s.value().isBlank()) ? s.value() : m.getName();
                    if (alias.equals(methodName)) {
                        m.setAccessible(true);
                        candidates.add(new MethodCandidate(bean, m));
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        if (candidates.isEmpty()) {
            return Mono.just(ResponseEntity.status(404).body(Map.of("error", "no shared method '" + methodName + "' found locally")));
        }

        return bodyMono.defaultIfEmpty(new byte[0]).flatMap(body -> {
            List<Mono<AbstractMap.SimpleEntry<String, Object>>> calls = candidates.stream().map(c ->
                    Mono.fromCallable(() -> {
                        try {
                            Method m = c.method;
                            Object res;
                            if (m.getParameterCount() == 0) {
                                res = m.invoke(c.bean);
                            } else if (m.getParameterCount() == 1) {
                                Class<?> p = m.getParameterTypes()[0];
                                Object arg;
                                if (p.equals(String.class)) arg = new String(body);
                                else if (p.equals(byte[].class)) arg = body;
                                else {
                                    // try to deserialize JSON body into the parameter type
                                    try {
                                        arg = objectMapper.readValue(body, p);
                                    } catch (Exception ex) {
                                        return new AbstractMap.SimpleEntry<String, Object>(c.bean.getClass().getSimpleName(), (Object) Map.of("error", "json-deserialize-error: " + ex.getMessage()));
                                    }
                                }
                                res = m.invoke(c.bean, arg);
                            } else {
                                res = Map.of("error", "unsupported-shared-method-signature");
                            }
                            return new AbstractMap.SimpleEntry<String, Object>(c.bean.getClass().getSimpleName(), (Object) Map.of("result", res));
                        } catch (Throwable e) {
                            return new AbstractMap.SimpleEntry<String, Object>(c.bean.getClass().getSimpleName(), (Object) Map.of("error", e.getMessage()));
                        }
                    }).subscribeOn(Schedulers.boundedElastic())
            ).collect(Collectors.toList());

            return Flux.mergeSequential(calls).collectList().map(list -> {
                Map<String,Object> aggregated = list.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                return ResponseEntity.ok(aggregated);
            });
        });
    }

    private static class MethodCandidate {
        final Object bean;
        final Method method;

        MethodCandidate(Object bean, Method method) { this.bean = bean; this.method = method; }
    }
}
