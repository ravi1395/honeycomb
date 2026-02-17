package com.honeycomb.core.bench;

import com.honeycomb.core.model.CellAddress;
import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMH benchmark suite for routing policy selection logic.
 *
 * <p>Measures the throughput and latency of different routing algorithms
 * (random, round-robin, first) across varying numbers of targets.
 * Uses inline logic to avoid RoutingPolicyService constructor dependencies.</p>
 *
 * @since 1.4.3
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 1, jvmArgs = {"-Xms256m", "-Xmx512m"})
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Benchmark)
public class RoutingPolicyBenchmark {

    private List<CellAddress> targets;
    private final AtomicInteger rrCounter = new AtomicInteger(0);

    @Param({"3", "10", "50"})
    private int targetCount;

    @Setup(Level.Trial)
    public void setup() {
        targets = new ArrayList<>();
        for (int i = 0; i < targetCount; i++) {
            CellAddress addr = new CellAddress();
            addr.setHost("host-" + i);
            addr.setPort(8080 + i);
            targets.add(addr);
        }
    }

    @Benchmark
    public CellAddress routeRandom() {
        return targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
    }

    @Benchmark
    public CellAddress routeRoundRobin() {
        int idx = Math.abs(rrCounter.getAndIncrement() % targets.size());
        return targets.get(idx);
    }

    @Benchmark
    public CellAddress routeFirst() {
        return targets.getFirst();
    }

    @Benchmark
    public List<CellAddress> routeAll() {
        return Collections.unmodifiableList(targets);
    }
}
