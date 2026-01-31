package com.example.honeycomb.web;

import com.example.honeycomb.annotations.Cell;
import com.example.honeycomb.annotations.Sharedwall;
import com.example.honeycomb.service.CellRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/honeycomb/shared")
@Tag(name = "Shared Method Dispatcher", description = "Invoke shared methods across cells using @Sharedwall annotations")
@SuppressWarnings("null")
public class SharedwallDispatcherController {
    private static final Logger log = LoggerFactory.getLogger(SharedwallDispatcherController.class);
    private final ApplicationContext ctx;
    private final ObjectMapper objectMapper;

    public SharedwallDispatcherController(ApplicationContext ctx, CellRegistry registry, ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.objectMapper = objectMapper;
    }

    /**
     * Generic entrypoint that invokes local methods marked with `@Sharedwall`.
     * It looks up beans annotated with `@Cell` and finds methods with matching name or alias.
    * Supported method signatures: () , (String) , (byte[]).
    * For end-to-end reactive execution, shared methods should return Mono or Flux.
     */
    @Operation(
            summary = "Invoke a shared method",
            description = "Dispatches a call to methods annotated with @Sharedwall in @Cell beans. " +
                    "Supports zero, single, or multi-parameter methods with JSON body binding."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Method invoked successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied - caller not in allowedFrom list"),
            @ApiResponse(responseCode = "404", description = "Method not found"),
            @ApiResponse(responseCode = "500", description = "Invocation error")
    })
    @PostMapping("/{methodName}")
    public Mono<ResponseEntity<Map<String,Object>>> dispatch(
            @Parameter(description = "Name or alias of the shared method to invoke")
            @PathVariable String methodName,
            @RequestHeader MultiValueMap<String, String> headers,
            @RequestBody(required = false) Mono<byte[]> bodyMono
    ) {
        log.debug("Dispatch shared method={}, headers={}, bodyMono={}", methodName, headers, bodyMono);
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
            log.info("No shared method '{}' found locally", methodName);
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

                List<Mono<AbstractMap.SimpleEntry<String, Object>>> calls = candidates.stream()
                        .map(c -> invokeCandidate(c, headers, body, rootNode))
                        .collect(Collectors.toList());

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

    private Mono<AbstractMap.SimpleEntry<String, Object>> invokeCandidate(MethodCandidate c,
                                                                          MultiValueMap<String, String> headers,
                                                                          byte[] body,
                                                                          com.fasterxml.jackson.databind.JsonNode rootNode) {
        return Mono.defer(() -> {
            try {
                String cellName = c.bean.getClass().getSimpleName();
                String targetMethod = c.method.getName();
                log.debug("Invoking candidate {}.{}", cellName, targetMethod);
                Method m = c.method;
                final String caller = headers.getFirst("X-From-Cell");
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
                            log.warn("Access denied invoking {}.{} from caller={}", cellName, targetMethod, caller);
                            return Mono.just(new AbstractMap.SimpleEntry<String, Object>(cellName, (Object) Map.of("error", "access-denied: caller='" + caller + "' not allowed")));
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
                            log.warn("JSON deserialize error for {}.{}: {}", cellName, targetMethod, ex.getMessage());
                            return Mono.just(new AbstractMap.SimpleEntry<String, Object>(cellName, (Object) Map.of("error", "json-deserialize-error: " + ex.getMessage())));
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
                log.debug("Invocation {}.{} succeeded", cellName, m.getName());
                return adaptResult(cellName, res);
            } catch (Throwable e) {
                return Mono.error(e);
            }
        }).onErrorResume(e -> {
            String cellName = c.bean.getClass().getSimpleName();
            String targetMethod = c.method.getName();
            String emsg = e == null ? "" : e.getMessage();
            if (emsg == null || emsg.isBlank()) {
                Throwable cause = e == null ? null : e.getCause();
                emsg = (cause == null || cause.getMessage() == null) ? "invocation-error" : cause.getMessage();
            }
            log.error("Invocation error on {}.{}: {}", cellName, targetMethod, emsg, e);
            return Mono.just(new AbstractMap.SimpleEntry<String, Object>(cellName, (Object) Map.of("error", emsg)));
        });
    }

    private Mono<AbstractMap.SimpleEntry<String, Object>> adaptResult(String cellName, Object res) {
        if (res instanceof Mono<?> mono) {
            return mono.defaultIfEmpty(null)
                    .map(val -> new AbstractMap.SimpleEntry<String, Object>(cellName, (Object) Map.of("result", val)));
        }
        if (res instanceof Flux<?> flux) {
            return flux.collectList()
                    .map(list -> new AbstractMap.SimpleEntry<String, Object>(cellName, (Object) Map.of("result", list)));
        }
        if (res instanceof Publisher<?> publisher) {
            return Flux.from(publisher).collectList()
                    .map(list -> new AbstractMap.SimpleEntry<String, Object>(cellName, (Object) Map.of("result", list)));
        }
        return Mono.just(new AbstractMap.SimpleEntry<String, Object>(cellName, (Object) Map.of("result", res)));
    }
}
