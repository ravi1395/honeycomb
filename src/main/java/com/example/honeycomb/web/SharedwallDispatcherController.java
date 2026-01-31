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
            // attempt to parse JSON body
            com.fasterxml.jackson.databind.JsonNode tmpRoot = null;
            try {
                tmpRoot = objectMapper.readTree(body);
            } catch (Exception ignored) {
                // leave as null on parse failure
            }
            final com.fasterxml.jackson.databind.JsonNode rootNode = tmpRoot;

            List<Mono<AbstractMap.SimpleEntry<String, Object>>> calls = candidates.stream().map(c ->
                    Mono.fromCallable(() -> {
                        try {
                            Method m = c.method;
                            final String caller = headers.getFirst("X-From-Cell") != null ? headers.getFirst("X-From-Cell") : headers.getFirst("X-From-Domain");
                            // enforce allowed-from restrictions if declared on the method
                            Sharedwall allowedAnnTop = m.getAnnotation(Sharedwall.class);
                            if (allowedAnnTop != null) {
                                String[] allowedTop = allowedAnnTop.allowedFrom();
                                if (allowedTop != null && allowedTop.length > 0) {
                                    boolean okTop = false;
                                    if (caller != null) {
                                        for (String a : allowedTop) {
                                            if ("*".equals(a) || a.equalsIgnoreCase(caller)) { okTop = true; break; }
                                        }
                                    }
                                    if (!okTop) {
                                        return new AbstractMap.SimpleEntry<String, Object>(c.bean.getClass().getSimpleName(), (Object) Map.of("error", "access-denied: caller='" + caller + "' not allowed"));
                                    }
                                }
                            }
                            Object res;
                            int paramCount = m.getParameterCount();
                            if (paramCount == 0) {
                                res = m.invoke(c.bean);
                            } else if (paramCount == 1) {
                                java.lang.reflect.Parameter param = m.getParameters()[0];
                                Class<?> p = param.getType();
                                Object arg;
                                if (p.equals(String.class)) arg = new String(body);
                                else if (p.equals(byte[].class)) arg = body;
                                else {
                                    try {
                                        com.fasterxml.jackson.databind.JavaType jt = objectMapper.getTypeFactory().constructType(param.getParameterizedType());
                                        if (rootNode != null) arg = objectMapper.convertValue(rootNode, jt);
                                        else arg = objectMapper.readValue(body, jt);
                                    } catch (Exception ex) {
                                        return new AbstractMap.SimpleEntry<String, Object>(c.bean.getClass().getSimpleName(), (Object) Map.of("error", "json-deserialize-error: " + ex.getMessage()));
                                    }
                                }
                                res = m.invoke(c.bean, arg);
                            } else {
                                // multi-arg: map using JSON array by index or JSON object by parameter names
                                Object[] args = new Object[paramCount];
                                // top-level check already performed; proceed with arg mapping
                                java.lang.reflect.Parameter[] params = m.getParameters();
                                if (rootNode != null && rootNode.isArray()) {
                                    for (int i = 0; i < paramCount; i++) {
                                        com.fasterxml.jackson.databind.JavaType jt = objectMapper.getTypeFactory().constructType(params[i].getParameterizedType());
                                        com.fasterxml.jackson.databind.JsonNode el = rootNode.size() > i ? rootNode.get(i) : null;
                                        if (el == null || el.isNull()) args[i] = null;
                                        else args[i] = objectMapper.convertValue(el, jt);
                                    }
                                } else if (rootNode != null && rootNode.isObject()) {
                                    for (int i = 0; i < paramCount; i++) {
                                        String pname = params[i].getName();
                                        com.fasterxml.jackson.databind.JavaType jt = objectMapper.getTypeFactory().constructType(params[i].getParameterizedType());
                                        com.fasterxml.jackson.databind.JsonNode el = rootNode.get(pname);
                                        if (el == null || el.isNull()) args[i] = null;
                                        else args[i] = objectMapper.convertValue(el, jt);
                                    }
                                } else {
                                    // fallback: treat whole body as first string param
                                    args[0] = new String(body);
                                    for (int i = 1; i < paramCount; i++) args[i] = null;
                                }
                                res = m.invoke(c.bean, args);
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
