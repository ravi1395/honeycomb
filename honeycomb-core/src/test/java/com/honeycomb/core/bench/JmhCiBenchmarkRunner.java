package com.honeycomb.core.bench;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * CI-integrated JMH benchmark runner.
 *
 * <p>Run all Honeycomb benchmarks in a single JUnit test, producing
 * a JSON report at {@code target/jmh-report.json}.</p>
 *
 * <p>Gated by the {@code honeycomb.jmh} system property to avoid
 * running during normal {@code mvn test}. To execute:</p>
 * <pre>{@code
 * mvn test -pl honeycomb-core -Dhoneycomb.jmh=true -Dtest=JmhCiBenchmarkRunner
 * }</pre>
 *
 * @since 1.4.3
 */
@Tag("benchmark")
public class JmhCiBenchmarkRunner {

    @Test
    @EnabledIfSystemProperty(named = "honeycomb.jmh", matches = "true")
    void runAllBenchmarks() throws Exception {
        Options opts = new OptionsBuilder()
                .include("com\\.honeycomb\\.core\\.bench\\..*")
                .forks(1)
                .warmupIterations(2)
                .measurementIterations(3)
                .resultFormat(ResultFormatType.JSON)
                .result("target/jmh-report.json")
                .build();

        new Runner(opts).run();
    }

    @Test
    @EnabledIfSystemProperty(named = "honeycomb.jmh", matches = "true")
    void runDataStoreBenchmark() throws Exception {
        Options opts = new OptionsBuilder()
                .include(CellDataStoreBenchmark.class.getName())
                .forks(1)
                .warmupIterations(2)
                .measurementIterations(3)
                .resultFormat(ResultFormatType.JSON)
                .result("target/jmh-datastore-report.json")
                .build();

        new Runner(opts).run();
    }
}
