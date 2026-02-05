package com.example.honeycomb.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SharedwallMethodCacheBenchmarkTest {

    @Autowired
    private SharedwallMethodCache cache;

    @Test
    void benchmarkCacheLookup() {
        long rebuildMs = cache.rebuild();

        int iterations = 10_000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            cache.getCandidates("price");
        }
        long lookupMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("Sharedwall cache rebuild: " + rebuildMs + " ms");
        System.out.println("Sharedwall cache lookup (" + iterations + " iterations): " + lookupMs + " ms");
    }
}
