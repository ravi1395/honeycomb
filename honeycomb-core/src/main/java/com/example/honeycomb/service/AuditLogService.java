package com.example.honeycomb.service;

import com.example.honeycomb.config.HoneycombAuditProperties;
import com.example.honeycomb.dto.AuditEvent;
import com.example.honeycomb.util.HoneycombConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Service
public class AuditLogService {
    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final Deque<AuditEvent> events;
    private final int maxEntries;
    private final Sinks.Many<AuditEvent> sink;

    public AuditLogService(HoneycombAuditProperties props) {
        this.maxEntries = Math.max(50, props.getMaxEntries());
        this.events = new LinkedList<>();
        this.sink = Sinks.many().multicast().onBackpressureBuffer();
    }

    public void record(String actor, String action, String cell, String status, Map<String, Object> details) {
        AuditEvent event = new AuditEvent(Instant.now(), actor, action, cell, status, details == null ? Map.of() : details);
        synchronized (events) {
            events.addFirst(event);
            while (events.size() > maxEntries) {
                events.removeLast();
            }
        }
        log.info(HoneycombConstants.Messages.AUDIT_LOG, action, cell, status, actor, details);
        sink.tryEmitNext(event);
    }

    public List<AuditEvent> list(int limit) {
        int size = Math.max(1, limit);
        List<AuditEvent> out = new ArrayList<>(size);
        synchronized (events) {
            int count = 0;
            for (AuditEvent e : events) {
                out.add(e);
                if (++count >= size) break;
            }
        }
        return out;
    }

    public Flux<AuditEvent> stream() {
        return sink.asFlux();
    }
}
