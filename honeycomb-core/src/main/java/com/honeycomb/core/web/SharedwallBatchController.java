package com.honeycomb.core.web;

import com.honeycomb.core.dto.BatchInvokeRequest;
import com.honeycomb.core.dto.BatchInvokeResponse;
import com.honeycomb.core.util.HoneycombConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provides batch (parallel) and async (fire-and-forget) invocation of shared methods.
 * <p>
 * {@code POST /honeycomb/shared/batch} – invoke multiple shared methods in parallel
 * and return all results in a single response.
 * <p>
 * {@code POST /honeycomb/shared/async/{methodName}} – fire-and-forget: accepts the
 * request immediately, invokes the method on a background scheduler, and returns a
 * tracking ID.
 */
@RestController
@RequestMapping(HoneycombConstants.Paths.HONEYCOMB_SHARED)
@Tag(name = "Shared Method Batch & Async",
        description = "Batch and fire-and-forget invocation of shared methods")
@SuppressWarnings("null")
public class SharedwallBatchController {
    private static final Logger log = LoggerFactory.getLogger(SharedwallBatchController.class);

    private final SharedwallDispatcherController dispatcher;
    private final MeterRegistry meterRegistry;

    public SharedwallBatchController(SharedwallDispatcherController dispatcher,
                                     MeterRegistry meterRegistry) {
        this.dispatcher = dispatcher;
        this.meterRegistry = meterRegistry;
    }

    // ────────────────────────── Batch invoke ──────────────────────────

    @Operation(summary = "Invoke multiple shared methods in parallel",
            description = "Accepts an array of invocation requests and returns results for each. "
                    + "Invocations are executed in parallel and results are returned in the same order.")
    @PostMapping(value = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<List<BatchInvokeResponse>>> batchInvoke(
            @RequestBody List<BatchInvokeRequest> requests,
            @RequestHeader MultiValueMap<String, String> headers) {

        meterRegistry.counter("honeycomb.shared.batch.requests", "size",
                String.valueOf(requests == null ? 0 : requests.size())).increment();

        if (requests == null || requests.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(List.of()));
        }

        List<Mono<BatchInvokeResponse>> monos = requests.stream()
                .map(req -> invokeSingle(req, headers))
                .toList();

        return Flux.mergeSequential(monos)
                .collectList()
                .map(ResponseEntity::ok);
    }

    private Mono<BatchInvokeResponse> invokeSingle(BatchInvokeRequest req,
                                                    MultiValueMap<String, String> originalHeaders) {
        long start = System.currentTimeMillis();

        // Build per-request headers (carry through auth, add version)
        MultiValueMap<String, String> perRequestHeaders = new LinkedMultiValueMap<>(originalHeaders);
        perRequestHeaders.set(HoneycombConstants.Headers.SHARED_VERSION, req.version());

        byte[] bodyBytes;
        try {
            bodyBytes = req.body() == null ? new byte[0]
                    : new ObjectMapper().writeValueAsBytes(req.body());
        } catch (Exception ex) {
            return Mono.just(new BatchInvokeResponse(
                    req.methodName(), req.version(), "error", null,
                    "Failed to serialize body: " + ex.getMessage(),
                    System.currentTimeMillis() - start));
        }

        // Delegate to the existing dispatcher
        return dispatcher.dispatch(req.methodName(), perRequestHeaders, Mono.just(bodyBytes))
                .map(response -> {
                    long durationMs = System.currentTimeMillis() - start;
                    Map<String, Object> body = response.getBody();
                    boolean isError = body != null && body.containsKey(HoneycombConstants.JsonKeys.ERROR);
                    String status = (response.getStatusCode().is2xxSuccessful() && !isError)
                            ? HoneycombConstants.Status.OK
                            : HoneycombConstants.Status.ERROR;
                    String errorMsg = isError && body != null
                            ? String.valueOf(body.get(HoneycombConstants.JsonKeys.ERROR))
                            : null;
                    return new BatchInvokeResponse(
                            req.methodName(), req.version(), status,
                            isError ? null : body, errorMsg, durationMs);
                })
                .onErrorResume(ex -> {
                    long durationMs = System.currentTimeMillis() - start;
                    meterRegistry.counter("honeycomb.shared.batch.errors",
                            "method", req.methodName(), "version", req.version()).increment();
                    return Mono.just(new BatchInvokeResponse(
                            req.methodName(), req.version(), "error", null,
                            ex.getMessage(), durationMs));
                });
    }

    // ────────────────────────── Async (fire-and-forget) ──────────────────────────

    @Operation(summary = "Fire-and-forget invocation of a shared method",
            description = "Immediately returns a tracking ID while the invocation runs "
                    + "asynchronously on a background scheduler.")
    @PostMapping(value = "/async/{methodName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> asyncInvoke(
            @PathVariable String methodName,
            @RequestHeader MultiValueMap<String, String> headers,
            @RequestBody(required = false) Mono<byte[]> bodyMono) {

        String trackingId = UUID.randomUUID().toString();
        String version = headers.getFirst(HoneycombConstants.Headers.SHARED_VERSION);
        meterRegistry.counter("honeycomb.shared.async.accepted",
                "method", methodName, "version", version == null ? "v1" : version).increment();

        // Fire-and-forget: subscribe on a background scheduler
        dispatcher.dispatch(methodName, headers, bodyMono)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        result -> {
                            meterRegistry.counter("honeycomb.shared.async.completed",
                                    "method", methodName, "outcome", "success").increment();
                            log.debug("Async invocation {} completed for {}: {}", trackingId, methodName, result.getStatusCode());
                        },
                        error -> {
                            meterRegistry.counter("honeycomb.shared.async.completed",
                                    "method", methodName, "outcome", "error").increment();
                            log.warn("Async invocation {} failed for {}: {}", trackingId, methodName, error.getMessage());
                        }
                );

        return Mono.just(ResponseEntity.accepted()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "trackingId", trackingId,
                        HoneycombConstants.JsonKeys.STATUS, "accepted",
                        HoneycombConstants.JsonKeys.METHOD, methodName
                )));
    }
}
