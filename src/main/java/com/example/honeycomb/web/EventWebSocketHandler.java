package com.example.honeycomb.web;

import com.example.honeycomb.dto.AuditEvent;
import com.example.honeycomb.service.AuditLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Mono;

@Component
@SuppressWarnings("null")
public class EventWebSocketHandler implements WebSocketHandler {
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public EventWebSocketHandler(AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Override
    @NonNull
    public Mono<Void> handle(@NonNull WebSocketSession session) {
        return session.send(
                auditLogService.stream()
                        .map(this::toJson)
                        .map(session::textMessage)
        );
    }

    private String toJson(AuditEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"serialization\"}";
        }
    }
}
