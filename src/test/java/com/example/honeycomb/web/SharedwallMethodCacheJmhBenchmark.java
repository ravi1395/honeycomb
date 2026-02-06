package com.example.honeycomb.web;

import com.example.honeycomb.HoneycombApplication;
import org.openjdk.jmh.annotations.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 2)
@Measurement(iterations = 5)
@State(Scope.Benchmark)
public class SharedwallMethodCacheJmhBenchmark {

    private ConfigurableApplicationContext context;
    private com.example.honeycomb.service.SharedwallMethodCache cache;

    @Setup(Level.Trial)
    public void setup() {
        context = new SpringApplicationBuilder(HoneycombApplication.class)
            .web(WebApplicationType.NONE)
            .run();
        cache = context.getBean(com.example.honeycomb.service.SharedwallMethodCache.class);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Benchmark
    public int lookupPrice() {
        return cache.getCandidates("price").size();
    }

    @Benchmark
    public long rebuildCache() {
        return cache.rebuild();
    }
}
