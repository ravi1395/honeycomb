package com.honeycomb.core.client;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultSharedwallResponseMapperTest {

    private final DefaultSharedwallResponseMapper mapper = new DefaultSharedwallResponseMapper();

    @Test
    void firstResultReturnsInnerResult() {
        Object out = mapper.map(sampleEnvelope(), SharedwallEnvelopeMode.FIRST_RESULT, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) out;
        assertEquals(2, result.get("receivedKeys"));
    }

    @Test
    void strictSingleThrowsWhenMultipleCells() {
        assertThrows(IllegalStateException.class,
                () -> mapper.map(sampleEnvelope(), SharedwallEnvelopeMode.STRICT_SINGLE_CELL, null));
    }

    @Test
    void strictSingleCanTargetCell() {
        Object out = mapper.map(sampleEnvelope(), SharedwallEnvelopeMode.STRICT_SINGLE_CELL, "ExampleSharedService");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) out;
        assertEquals(2, result.get("receivedKeys"));
    }

    @Test
    void mergedResultsReturnsMapByCellWithResultValues() {
        Object out = mapper.map(sampleEnvelope(), SharedwallEnvelopeMode.MERGED_RESULTS, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) out;
        @SuppressWarnings("unchecked")
        Map<String, Object> example = (Map<String, Object>) merged.get("ExampleSharedService");
        assertEquals(2, example.get("receivedKeys"));
    }

    private Map<String, Object> sampleEnvelope() {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("ExampleSharedService", Map.of("result", Map.of("receivedKeys", 2)));
        envelope.put("AnotherCell", Map.of("result", "ok"));
        return envelope;
    }
}
