package com.honeycomb.core.client;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DefaultSharedwallResponseMapper implements SharedwallResponseMapper {
    @Override
    public Object map(Map<String, Object> envelope, SharedwallEnvelopeMode mode, String targetCell) {
        if (envelope == null) {
            return null;
        }

        SharedwallEnvelopeMode resolvedMode = mode == null ? SharedwallEnvelopeMode.FIRST_RESULT : mode;
        return switch (resolvedMode) {
            case RAW_ENVELOPE -> envelope;
            case FIRST_RESULT -> firstResult(envelope);
            case STRICT_SINGLE_CELL -> strictSingleResult(envelope, targetCell);
            case MERGED_RESULTS -> mergedResults(envelope);
        };
    }

    private Object firstResult(Map<String, Object> envelope) {
        if (envelope.isEmpty()) {
            return null;
        }
        return extractResult(envelope.values().iterator().next());
    }

    private Object strictSingleResult(Map<String, Object> envelope, String targetCell) {
        if (targetCell != null && !targetCell.isBlank()) {
            Object byCell = envelope.get(targetCell);
            if (byCell == null) {
                throw new IllegalStateException("No sharedwall result found for target cell: " + targetCell);
            }
            return extractResult(byCell);
        }
        if (envelope.size() != 1) {
            throw new IllegalStateException("Expected exactly one sharedwall cell result, got: " + envelope.size());
        }
        return extractResult(envelope.values().iterator().next());
    }

    private Object mergedResults(Map<String, Object> envelope) {
        Map<String, Object> merged = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : envelope.entrySet()) {
            merged.put(entry.getKey(), extractResult(entry.getValue()));
        }
        return merged;
    }

    private Object extractResult(Object value) {
        if (value instanceof Map<?, ?> map && map.containsKey("result")) {
            return map.get("result");
        }
        return value;
    }
}
