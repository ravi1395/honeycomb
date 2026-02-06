package com.example.honeycomb.service;

import com.example.honeycomb.config.HoneycombRoutingProperties;
import com.example.honeycomb.model.CellAddress;
import com.example.honeycomb.util.HoneycombConstants;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RoutingPolicyService {
    private final HoneycombRoutingProperties props;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    // use ThreadLocalRandom for reduced contention
    private final Map<String, LatencyStats> latencyStats = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final io.micrometer.core.instrument.Counter routingSelects;

    public RoutingPolicyService(HoneycombRoutingProperties props,
                                CircuitBreakerRegistry circuitBreakerRegistry,
                                MeterRegistry meterRegistry) {
        this.props = props;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.meterRegistry = meterRegistry;
        this.routingSelects = meterRegistry.counter("honeycomb.routing.selects");
    }

    public List<CellAddress> selectTargets(String cell, List<CellAddress> addresses, String policyOverride) {
        // increment a simple counter to observe routing selection rate
        if (routingSelects != null) routingSelects.increment();
        if (addresses == null || addresses.isEmpty()) return List.of();
        String policy = policyOverride == null || policyOverride.isBlank() ? props.resolvePolicy(cell) : policyOverride;
        String normalized = policy.toLowerCase();
        return switch (normalized) {
            case HoneycombConstants.RoutingPolicies.ONE, HoneycombConstants.RoutingPolicies.RANDOM
                -> List.of(addresses.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(addresses.size())));
            case HoneycombConstants.RoutingPolicies.ROUND_ROBIN -> List.of(roundRobin(cell, addresses));
            case HoneycombConstants.RoutingPolicies.WEIGHTED -> List.of(weighted(cell, addresses));
            case HoneycombConstants.RoutingPolicies.LEAST_LATENCY -> List.of(leastLatency(cell, addresses));
            case HoneycombConstants.RoutingPolicies.CIRCUIT_AWARE -> selectCircuitAware(cell, addresses);
            case HoneycombConstants.RoutingPolicies.ALL -> addresses;
            default -> addresses;
        };
    }

    public void recordLatency(String cell, CellAddress address, long durationMs, boolean success) {
        if (address == null) return;
        String key = addressKey(cell, address);
        latencyStats.compute(key, (k, prev) -> {
            if (prev == null) return LatencyStats.from(durationMs, success);
            prev.update(durationMs, success);
            return prev;
        });
    }

    private CellAddress roundRobin(String cell, List<CellAddress> addresses) {
        AtomicInteger counter = counters.computeIfAbsent(
            cell == null ? HoneycombConstants.ConfigKeys.GLOBAL_ALL : cell,
            k -> new AtomicInteger()
        );
        int idx = Math.floorMod(counter.getAndIncrement(), addresses.size());
        return addresses.get(idx);
    }

    private CellAddress weighted(String cell, List<CellAddress> addresses) {
        Map<String, Integer> weights = props.resolveWeights(cell);
        // cumulative weight pick without list expansion
        int total = 0;
        int n = addresses.size();
        int[] ws = new int[n];
        for (int i = 0; i < n; i++) {
            CellAddress addr = addresses.get(i);
            String key = addr.getHost() + HoneycombConstants.Names.SEPARATOR_COLON + addr.getPort();
            int w = Math.max(1, weights.getOrDefault(key, 1));
            ws[i] = w;
            total += w;
        }
        if (total <= 0) return addresses.get(0);
        int r = java.util.concurrent.ThreadLocalRandom.current().nextInt(total);
        int cum = 0;
        for (int i = 0; i < n; i++) {
            cum += ws[i];
            if (r < cum) return addresses.get(i);
        }
        return addresses.get(0);
    }

    private CellAddress leastLatency(String cell, List<CellAddress> addresses) {
        return addresses.stream()
                .min(Comparator.comparingDouble(a -> latencyAvgMs(cell, a)))
                .orElseGet(() -> roundRobin(cell, addresses));
    }

    private List<CellAddress> selectCircuitAware(String cell, List<CellAddress> addresses) {
        List<CellAddress> closed = new ArrayList<>();
        for (CellAddress addr : addresses) {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(circuitName(cell, addr));
            if (cb.getState() != CircuitBreaker.State.OPEN) {
                closed.add(addr);
            }
        }
        if (closed.isEmpty()) return addresses;
        return List.of(roundRobin(cell, closed));
    }

    private double latencyAvgMs(String cell, CellAddress address) {
        LatencyStats stats = latencyStats.get(addressKey(cell, address));
        return stats == null ? Double.MAX_VALUE : stats.avgMs;
    }

    private String addressKey(String cell, CellAddress address) {
        String c = (cell == null || cell.isBlank()) ? "__all__" : cell;
        return c + "@" + address.getHost() + ":" + address.getPort();
    }

    public String circuitName(String cell, CellAddress address) {
        return "cell@" + addressKey(cell, address);
    }

    private static class LatencyStats {
        private static final double ALPHA = 0.2;
        private double avgMs;

        static LatencyStats from(long durationMs, boolean success) {
            LatencyStats s = new LatencyStats();
            s.avgMs = durationMs;
            return s;
        }

        void update(long durationMs, boolean success) {
            avgMs = (ALPHA * durationMs) + ((1 - ALPHA) * avgMs);
        }
    }
}
