package com.honeycomb.core.bench;

import com.honeycomb.core.service.InMemoryCellDataStore;
import com.honeycomb.core.service.CellDataStore;
import org.openjdk.jmh.annotations.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark suite for {@link CellDataStore} CRUD operations.
 *
 * <p>Measures throughput and latency of the in-memory data store
 * for create, read, list, update, and delete operations.</p>
 *
 * <p>Run with: {@code mvn -Pjmh test -pl honeycomb-core}</p>
 *
 * @since 1.4.3
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(value = 1, jvmArgs = {"-Xms256m", "-Xmx512m"})
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Benchmark)
public class CellDataStoreBenchmark {

    private CellDataStore store;

    @Param({"10", "100", "1000"})
    private int itemCount;

    @Setup(Level.Trial)
    public void setup() {
        store = new InMemoryCellDataStore();
        for (int i = 0; i < itemCount; i++) {
            store.create("benchCell", Map.of(
                    "id", "item-" + i,
                    "name", "Item " + i,
                    "value", i
            )).block();
        }
    }

    @Benchmark
    public Object createItem() {
        return store.create("benchCell", Map.of("name", "new", "value", 42)).block();
    }

    @Benchmark
    public Object getItem() {
        return store.get("benchCell", "item-0").block();
    }

    @Benchmark
    public long listItems() {
        return store.list("benchCell").count().block();
    }

    @Benchmark
    public Object updateItem() {
        return store.update("benchCell", "item-0", Map.of("name", "updated", "value", 99)).block();
    }

    @Benchmark
    public Object deleteAndRecreate() {
        store.delete("benchCell", "item-0").block();
        return store.create("benchCell", Map.of("id", "item-0", "name", "Item 0", "value", 0)).block();
    }
}
