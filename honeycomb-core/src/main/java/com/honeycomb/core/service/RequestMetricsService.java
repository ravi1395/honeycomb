package com.honeycomb.core.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import com.honeycomb.core.util.HoneycombConstants;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Records per-cell request counts and latency timers via Micrometer
 * and provides windowed RPS (requests-per-second) snapshots.
 *
 * <p>The windowed RPS data is consumed by {@link AutoScaleService}
 * for autoscale evaluations and by {@link com.honeycomb.core.web.MetricsController}
 * for the admin dashboard.</p>
 *
 * @see com.honeycomb.core.web.RequestMetricsFilter
 */
@Service
public class RequestMetricsService {
    private final MeterRegistry registry;
    private final Map<String, LongAdder> cellCounts = new ConcurrentHashMap<>();
    private volatile Instant windowStart = Instant.now();

    public RequestMetricsService(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String cell, String route, int status, Duration duration) {
        String cellTag = cell == null ? HoneycombConstants.Messages.UNKNOWN : cell;
        registry.counter(HoneycombConstants.Metrics.REQUESTS, HoneycombConstants.Metrics.TAG_CELL, cellTag, HoneycombConstants.Metrics.TAG_ROUTE, route, HoneycombConstants.Metrics.TAG_STATUS, String.valueOf(status)).increment();
        Timer.builder(HoneycombConstants.Metrics.LATENCY)
            .tag(HoneycombConstants.Metrics.TAG_CELL, cellTag)
            .tag(HoneycombConstants.Metrics.TAG_ROUTE, route)
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
