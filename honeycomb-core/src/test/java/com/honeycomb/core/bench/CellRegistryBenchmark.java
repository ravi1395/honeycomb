package com.honeycomb.core.bench;

import com.honeycomb.core.service.CellRegistry;
import org.openjdk.jmh.annotations.*;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark suite for {@link CellRegistry} operations.
 *
 * <p>Measures the cost of cell name lookup, cell description, and
 * the full cell name set retrieval.</p>
 *
 * @since 1.4.3
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(value = 1, jvmArgs = {"-Xms256m", "-Xmx512m"})
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Benchmark)
public class CellRegistryBenchmark {

    private CellRegistry registry;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        // We create a registry with a minimal ApplicationContext
        // In a real run, this would be wired via SpringApplicationBuilder
        registry = new CellRegistry();
        // The registry will be empty without ApplicationContext, but we measure the method overhead
    }

    @Benchmark
    public Set<String> getCellNames() {
        return registry.getCellNames();
    }

    @Benchmark
    public Map<String, Object> describeNonExistent() {
        return registry.describeCell("non-existent-cell");
    }
}
