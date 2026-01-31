package com.example.honeycomb.dto;

import java.time.Instant;
import java.util.Map;

public record AuditEvent(
        Instant timestamp,
        String actor,
        String action,
        String cell,
        String status,
        Map<String, Object> details
) {
}
