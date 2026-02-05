package com.example.honeycomb.web;

import com.example.honeycomb.annotations.Sharedwall;
import com.example.honeycomb.service.SharedwallMethodCache;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.http.MediaType;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadLocalRandom;
import com.example.honeycomb.util.HoneycombConstants;

@RestController
@RequestMapping(HoneycombConstants.Paths.HONEYCOMB_SHARED)
@Tag(name = HoneycombConstants.Docs.TAG_SHARED_DISPATCHER,
    description = HoneycombConstants.Docs.TAG_SHARED_DISPATCHER_DESC)
@SuppressWarnings("null")
public class SharedwallDispatcherController {
    private static final Logger log = LoggerFactory.getLogger(SharedwallDispatcherController.class);
    private final ObjectMapper objectMapper;
    private final SharedwallMethodCache methodCache;
    private final Scheduler sharedScheduler;
    private final double logSampleRate;

    public SharedwallDispatcherController(ObjectMapper objectMapper,
                                          SharedwallMethodCache methodCache,
                                          @Value("${honeycomb.shared.scheduler:boundedElastic}") String schedulerType,
                                          @Value("${honeycomb.shared.log-sample-rate:0.1}") double logSampleRate) {
        this.objectMapper = objectMapper;
        this.methodCache = methodCache;
        this.sharedScheduler = "parallel".equalsIgnoreCase(schedulerType) ? Schedulers.parallel() : Schedulers.boundedElastic();
        this.logSampleRate = logSampleRate;
    }

        /**
         * Generic entrypoint that invokes local methods marked with `@Sharedwall`.
         * Supported method signatures: () , (String) , (byte[]).
         * For end-to-end reactive execution, shared methods should return Mono or Flux.
         */
    @Operation(
            summary = HoneycombConstants.Docs.SHARED_DISPATCH_SUMMARY,
            description = HoneycombConstants.Docs.SHARED_DISPATCH_DESC
    )
    @ApiResponses({
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_200, description = HoneycombConstants.Docs.SHARED_OK),
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_403, description = HoneycombConstants.Docs.SHARED_FORBIDDEN),
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_404, description = HoneycombConstants.Docs.SHARED_NOT_FOUND),
            @ApiResponse(responseCode = HoneycombConstants.Swagger.RESP_500, description = HoneycombConstants.Docs.SHARED_ERROR)
    })
    @PostMapping("/{methodName}")
    public Mono<ResponseEntity<Map<String,Object>>> dispatch(
            @Parameter(description = HoneycombConstants.Docs.SHARED_METHOD_PARAM)
            @PathVariable String methodName,
            @RequestHeader MultiValueMap<String, String> headers,
            @RequestBody(required = false) Mono<byte[]> bodyMono
    ) {
        logSampledDebug(HoneycombConstants.Messages.DISPATCH_SHARED_DEBUG, methodName, headers, bodyMono);
        return Mono.fromCallable(() -> methodCache.getCandidates(methodName))
                .subscribeOn(sharedScheduler)
                .flatMap(candidates -> {
                    logSampledDebug("Found {} shared candidates for method {}", candidates.size(), methodName);
                    if (candidates.isEmpty()) {
                    logSampledInfo(HoneycombConstants.Messages.SHARED_METHOD_NOT_FOUND, methodName);
                    return Mono.just(ResponseEntity.status(404)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(HoneycombConstants.JsonKeys.ERROR,
                            HoneycombConstants.ErrorKeys.NO_SHARED_METHOD
                                + HoneycombConstants.Names.SEPARATOR_COLON
                                + HoneycombConstants.Messages.SPACE
                                + methodName)));
                    }

                    String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
                    boolean expectsJson = contentType != null && contentType.toLowerCase().contains(MediaType.APPLICATION_JSON_VALUE);
                    return bodyMono.defaultIfEmpty(new byte[0]).flatMap(body ->
                            Mono.fromCallable(() -> {
                                        try {
                                            logSampledDebug("parsing body for {} (len={})", methodName, body == null ? 0 : body.length);
                                            if (!expectsJson) {
                                                return com.fasterxml.jackson.databind.node.NullNode.getInstance();
                                            }
                                            return objectMapper.readTree(body);
                                        } catch (Exception ex) {
                                            String message = ex.getMessage() == null ? HoneycombConstants.Messages.EMPTY : ex.getMessage();
                                            logSampledDebug("parse error for {}: {}", methodName, message);
                                            return com.fasterxml.jackson.databind.node.TextNode.valueOf("__PARSE_ERROR__:" + message);
                                        }
                                    })
                                    .subscribeOn(sharedScheduler)
                                    .flatMap(rootNode -> {
                                        logSampledDebug("rootNode for {}: {}", methodName, rootNode);
                                        // if parsing failed, short-circuit and return a JSON-deserialize error per-candidate
                                        if (rootNode != null && rootNode.isTextual() && rootNode.asText().startsWith("__PARSE_ERROR__:")) {
                                            String emsg = rootNode.asText().substring("__PARSE_ERROR__:".length());
                                            String beanName = candidates.size() > 0 ? candidates.get(0).getBean().getClass().getSimpleName() : "unknown";
                                            Map<String,Object> bodyMap = Map.of(beanName, Map.of(
                                                    HoneycombConstants.JsonKeys.ERROR,
                                                    HoneycombConstants.ErrorKeys.JSON_DESERIALIZE_ERROR
                                                            + HoneycombConstants.Names.SEPARATOR_COLON
                                                            + HoneycombConstants.Messages.SPACE
                                                            + emsg
                                            ));
                                            return Mono.just(ResponseEntity.ok()
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .body(bodyMap));
                                        }
                                        List<Mono<AbstractMap.SimpleEntry<String, Object>>> calls = candidates.stream()
                                                .map(c -> {
                                                    logSampledDebug("scheduling invocation for {}.{}", c.getBean().getClass().getSimpleName(), c.getMethod().getName());
                                                    return invokeCandidate(c, headers, body, rootNode);
                                                })
                                                .collect(Collectors.toList());

                                        return Flux.mergeSequential(calls).collectList().flatMap(list -> {
                                            Map<String,Object> aggregated = list.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                                            logSampledDebug("Shared dispatch aggregated result for {}: {}", methodName, aggregated);
                                            return Mono.fromCallable(() -> objectMapper.writeValueAsString(aggregated))
                                                .subscribeOn(sharedScheduler)
                                                .doOnNext(s -> logSampledDebug("Serialized response for {}: {}", methodName, s))
                                                    .thenReturn(ResponseEntity.ok()
                                                            .contentType(MediaType.APPLICATION_JSON)
                                                            .body(aggregated));
                                        });
                                    }));
                });
    }

    private Mono<AbstractMap.SimpleEntry<String, Object>> invokeCandidate(SharedwallMethodCache.MethodCandidate c,
                                                                          MultiValueMap<String, String> headers,
                                                                          byte[] body,
                                                                          com.fasterxml.jackson.databind.JsonNode rootNode) {
        return Mono.defer(() -> {
            try {
                String cellName = c.getBean().getClass().getSimpleName();
                String targetMethod = c.getMethod().getName();
                log.debug(HoneycombConstants.Messages.INVOKE_CANDIDATE, cellName, targetMethod);
                Method m = c.getMethod();
                final String caller = headers.getFirst(HoneycombConstants.Headers.FROM_CELL);
                // enforce allowed-from restrictions if declared on the method or interface
                Sharedwall allowedAnnTop = c.getSharedwall();
                if (allowedAnnTop != null) {
                    String[] allowedTop = allowedAnnTop.allowedFrom();
                    if (allowedTop != null && allowedTop.length > 0) {
                        boolean okTop = false;
                        if (caller != null) {
                            for (String a : allowedTop) {
                                if (HoneycombConstants.ConfigKeys.GLOBAL_WILDCARD.equals(a) || a.equalsIgnoreCase(caller)) {
                                    okTop = true;
                                    break;
                                }
                            }
                        }
                        if (!okTop) {
                            log.warn(HoneycombConstants.Messages.ACCESS_DENIED_INVOKE, cellName, targetMethod, caller);
                            return Mono.just(new AbstractMap.SimpleEntry<String, Object>(cellName, (Object) Map.of(
                                    HoneycombConstants.JsonKeys.ERROR,
                                        HoneycombConstants.ErrorKeys.ACCESS_DENIED
                                            + HoneycombConstants.Names.SEPARATOR_COLON
                                            + HoneycombConstants.Messages.SPACE
                                            + HoneycombConstants.Messages.CALLER_PREFIX
                                            + caller
                                            + HoneycombConstants.Messages.CALLER_NOT_ALLOWED_SUFFIX
                            )));
                        }
                    }
                }
                Object res;
                int paramCount = m.getParameterCount();
                if (paramCount == 0) {
                    res = m.invoke(c.getBean());
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
                            log.warn(HoneycombConstants.Messages.JSON_DESERIALIZE_ERROR, cellName, targetMethod, ex.getMessage());
                            return Mono.just(new AbstractMap.SimpleEntry<String, Object>(cellName, (Object) Map.of(
                                    HoneycombConstants.JsonKeys.ERROR,
                                        HoneycombConstants.ErrorKeys.JSON_DESERIALIZE_ERROR
                                            + HoneycombConstants.Names.SEPARATOR_COLON
                                            + HoneycombConstants.Messages.SPACE
                                            + ex.getMessage()
                            )));
                        }
                    }
                    res = m.invoke(c.getBean(), arg);
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
                    res = m.invoke(c.getBean(), args);
                }
                log.debug(HoneycombConstants.Messages.INVOCATION_SUCCESS, cellName, m.getName());
                return adaptResult(cellName, res)
                    .doOnNext(entry -> log.debug("adaptResult emitted for {}: {}", cellName, entry))
                    .doOnError(err -> log.error("adaptResult error for {}", cellName, err));
            } catch (Throwable e) {
                return Mono.error(e);
            }
        }).onErrorResume(e -> {
            String cellName = c.getBean().getClass().getSimpleName();
            String targetMethod = c.getMethod().getName();
            String emsg = e == null ? HoneycombConstants.Messages.EMPTY : e.getMessage();
            if (emsg == null || emsg.isBlank()) {
                Throwable cause = e == null ? null : e.getCause();
                emsg = (cause == null || cause.getMessage() == null) ? HoneycombConstants.ErrorKeys.INVOCATION_ERROR : cause.getMessage();
            }
            log.error(HoneycombConstants.Messages.INVOCATION_ERROR, cellName, targetMethod, emsg, e);
            return Mono.just(new AbstractMap.SimpleEntry<String, Object>(cellName, (Object) Map.of(HoneycombConstants.JsonKeys.ERROR, emsg)));
        });
    }

    private Mono<AbstractMap.SimpleEntry<String, Object>> adaptResult(String cellName, Object res) {
        if (res instanceof Mono<?> mono) {
                return mono.defaultIfEmpty(null)
                    .map(val -> new AbstractMap.SimpleEntry<String, Object>(cellName, (Object) Map.of(HoneycombConstants.JsonKeys.RESULT, val)));
        }
        if (res instanceof Flux<?> flux) {
                return flux.collectList()
                    .map(list -> new AbstractMap.SimpleEntry<String, Object>(cellName, (Object) Map.of(HoneycombConstants.JsonKeys.RESULT, list)));
        }
        if (res instanceof Publisher<?> publisher) {
                return Flux.from(publisher).collectList()
                    .map(list -> new AbstractMap.SimpleEntry<String, Object>(cellName, (Object) Map.of(HoneycombConstants.JsonKeys.RESULT, list)));
        }
        return Mono.just(new AbstractMap.SimpleEntry<String, Object>(cellName, (Object) Map.of(HoneycombConstants.JsonKeys.RESULT, res)));
    }

    private boolean shouldSample() {
        if (logSampleRate <= 0) return false;
        if (logSampleRate >= 1) return true;
        return ThreadLocalRandom.current().nextDouble() < logSampleRate;
    }

    private void logSampledDebug(String msg, Object... args) {
        if (log.isDebugEnabled() && shouldSample()) {
            log.debug(msg, args);
        }
    }

    private void logSampledInfo(String msg, Object... args) {
        if (log.isInfoEnabled() && shouldSample()) {
            log.info(msg, args);
        }
    }
}
