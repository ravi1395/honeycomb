package com.example.honeycomb.service;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.Map;

public class CellDataStoreTest {

    @Test
    void createGetUpdateDeleteFlow() {
        CellDataStore ds = new InMemoryCellDataStore();

        // create
        StepVerifier.create(ds.create("X", Map.of("name", "v1")))
                .assertNext(m -> {
                    assert m.get("id") != null;
                    assert m.get("name").equals("v1");
                }).verifyComplete();

        // list contains
        StepVerifier.create(ds.list("X")).expectNextCount(1).verifyComplete();

        // get by id
        ds.create("X", Map.of("id", "i1", "name", "n1")).block();
        StepVerifier.create(ds.get("X", "i1")).expectNextMatches(m -> m.get("name").equals("n1")).verifyComplete();

        // update
        StepVerifier.create(ds.update("X", "i1", Map.of("name", "n2")))
                .expectNextMatches(m -> m.get("id").equals("i1") && m.get("name").equals("n2"))
                .verifyComplete();

        // delete
        StepVerifier.create(ds.delete("X", "i1")).expectNext(true).verifyComplete();
        StepVerifier.create(ds.get("X", "i1")).verifyComplete();
    }
}
