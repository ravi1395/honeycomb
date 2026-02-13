package com.honeycomb.core.web;

import com.honeycomb.core.annotations.Sharedwall;
import com.honeycomb.core.service.SharedwallMethodCache;
import com.honeycomb.core.util.HoneycombConstants;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin endpoints for monitoring shared methods, circuit breaker states,
 * and cache diagnostics.
 */
@RestController
@RequestMapping(HoneycombConstants.Paths.HONEYCOMB_ADMIN + "/shared")
@Tag(name = "Shared Method Admin",
        description = "Admin & diagnostic endpoints for shared method dispatch")
public class SharedMethodAdminController {

    private final SharedwallMethodCache methodCache;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public SharedMethodAdminController(SharedwallMethodCache methodCache,
                                       CircuitBreakerRegistry circuitBreakerRegistry) {
        this.methodCache = methodCache;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    // ──────────── Method registry ────────────

    @Operation(summary = "List all registered shared methods with metadata")
    @GetMapping("/methods")
    public Mono<List<Map<String, Object>>> listRegisteredMethods() {
        return Mono.fromCallable(() -> {
            Map<String, List<SharedwallMethodCache.MethodCandidate>> all = methodCache.getAllCandidates();
            List<Map<String, Object>> result = new ArrayList<>();
            for (var entry : all.entrySet()) {
                for (var candidate : entry.getValue()) {
                    Sharedwall sw = candidate.getSharedwall();
                    String version = (sw == null || sw.version() == null || sw.version().isBlank())
                            ? "v1" : sw.version();
                    String[] allowedFrom = sw != null ? sw.allowedFrom() : new String[0];
                    var method = candidate.getMethod();
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("methodName", entry.getKey());
                    info.put("version", version);
                    info.put("cell", candidate.getBean().getClass().getSimpleName());
                    info.put("javaMethod", method.getDeclaringClass().getSimpleName() + "." + method.getName());
                    info.put("returnType", method.getGenericReturnType().getTypeName());
                    info.put("paramCount", method.getParameterCount());
                    info.put("allowedFrom", Arrays.asList(allowedFrom));
                    info.put("deprecated", method.isAnnotationPresent(Deprecated.class));
                    result.add(info);
                }
            }
            result.sort(Comparator.comparing((Map<String, Object> m) -> (String) m.get("methodName"))
                    .thenComparing(m -> (String) m.get("version")));
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ──────────── Circuit breakers ────────────

    @Operation(summary = "List all circuit breaker states for shared methods")
    @GetMapping("/circuit-breakers")
    public Mono<List<Map<String, Object>>> listCircuitBreakers() {
        return Mono.fromCallable(() ->
            circuitBreakerRegistry.getAllCircuitBreakers().stream()
                .filter(cb -> cb.getName().startsWith("shared-method@"))
                .map(cb -> {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("name", cb.getName());
                    info.put("state", cb.getState().name());
                    var metrics = cb.getMetrics();
                    info.put("failureRate", metrics.getFailureRate());
                    info.put("slowCallRate", metrics.getSlowCallRate());
                    info.put("numberOfBufferedCalls", metrics.getNumberOfBufferedCalls());
                    info.put("numberOfFailedCalls", metrics.getNumberOfFailedCalls());
                    info.put("numberOfSuccessfulCalls", metrics.getNumberOfSuccessfulCalls());
                    info.put("numberOfNotPermittedCalls", metrics.getNumberOfNotPermittedCalls());
                    info.put("numberOfSlowCalls", metrics.getNumberOfSlowCalls());
                    return info;
                })
                .sorted(Comparator.comparing(m -> (String) m.get("name")))
                .collect(Collectors.toList())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @Operation(summary = "Get circuit breaker state for a specific shared method")
    @GetMapping("/circuit-breakers/{methodName}/{version}")
    public Mono<Map<String, Object>> getCircuitBreaker(
            @PathVariable String methodName,
            @PathVariable String version) {
        String cbName = "shared-method@" + methodName + ":" + version;
        return Mono.fromCallable(() -> {
            try {
                CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(cbName);
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("name", cb.getName());
                info.put("state", cb.getState().name());
                var metrics = cb.getMetrics();
                info.put("failureRate", metrics.getFailureRate());
                info.put("slowCallRate", metrics.getSlowCallRate());
                info.put("numberOfBufferedCalls", metrics.getNumberOfBufferedCalls());
                info.put("numberOfFailedCalls", metrics.getNumberOfFailedCalls());
                info.put("numberOfSuccessfulCalls", metrics.getNumberOfSuccessfulCalls());
                info.put("numberOfNotPermittedCalls", metrics.getNumberOfNotPermittedCalls());
                return info;
            } catch (Exception ex) {
                return Map.<String, Object>of(
                        "name", cbName,
                        HoneycombConstants.JsonKeys.ERROR, "Circuit breaker not found: " + cbName);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Operation(summary = "Reset a circuit breaker for a shared method (force to CLOSED)")
    @PostMapping("/circuit-breakers/{methodName}/{version}/reset")
    public Mono<Map<String, Object>> resetCircuitBreaker(
            @PathVariable String methodName,
            @PathVariable String version) {
        String cbName = "shared-method@" + methodName + ":" + version;
        return Mono.fromCallable(() -> {
            try {
                CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(cbName);
                cb.reset();
                return Map.<String, Object>of(
                        "name", cbName,
                        "state", cb.getState().name(),
                        HoneycombConstants.JsonKeys.STATUS, HoneycombConstants.Status.OK);
            } catch (Exception ex) {
                return Map.<String, Object>of(
                        "name", cbName,
                        HoneycombConstants.JsonKeys.ERROR, "Circuit breaker not found: " + cbName);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ──────────── Cache diagnostics ────────────

    @Operation(summary = "Get shared method cache health and diagnostics")
    @GetMapping("/cache")
    public Mono<Map<String, Object>> cacheInfo() {
        return Mono.fromCallable(() -> {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("methodCount", methodCache.getMethodCount());
            info.put("lastRefreshDurationMs", methodCache.getLastRefreshDurationMs());
            info.put("lastRefreshAtMs", methodCache.getLastRefreshAtMs());
            info.put("lastRefreshAgeMs", methodCache.getLastRefreshAgeMs());
            info.put("nextAllowedRefreshAtMs", methodCache.getNextAllowedRefreshAtMs());
            info.put("consecutiveFailures", methodCache.getConsecutiveFailures());

            // Include per-method candidate counts
            Map<String, Integer> methodCounts = new LinkedHashMap<>();
            methodCache.getAllCandidates().forEach((name, candidates) ->
                    methodCounts.put(name, candidates.size()));
            info.put("methodCandidateCounts", methodCounts);
            return info;
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
