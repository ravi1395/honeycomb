package com.example.honeycomb.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Service
public class RequestMetricsService {
    private final MeterRegistry registry;
    private final Map<String, LongAdder> cellCounts = new ConcurrentHashMap<>();
    private volatile Instant windowStart = Instant.now();

    public RequestMetricsService(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String cell, String route, int status, Duration duration) {
        String cellTag = cell == null ? "unknown" : cell;
        registry.counter("honeycomb.requests", "cell", cellTag, "route", route, "status", String.valueOf(status)).increment();
        Timer.builder("honeycomb.latency")
                .tag("cell", cellTag)
                .tag("route", route)
                .register(registry)
                .record(duration);
        cellCounts.computeIfAbsent(cellTag, k -> new LongAdder()).increment();
    }

    public Map<String, Long> snapshotCounts() {
        Map<String, Long> out = new ConcurrentHashMap<>();
        cellCounts.forEach((k, v) -> out.put(k, v.sum()));
        return out;
    }

    public Map<String, Double> snapshotRpsAndReset(Duration window) {
        Instant now = Instant.now();
        Duration elapsed = Duration.between(windowStart, now);
        if (elapsed.isZero() || elapsed.isNegative()) {
            return Map.of();
        }
        if (elapsed.compareTo(window) < 0) {
            return Map.of();
        }
        Map<String, Double> out = new ConcurrentHashMap<>();
        cellCounts.forEach((k, v) -> {
            long count = v.sumThenReset();
            out.put(k, count / (double) elapsed.getSeconds());
        });
        windowStart = now;
        return out;
    }
}
