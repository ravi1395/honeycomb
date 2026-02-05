package com.example.honeycomb.web;

import com.example.honeycomb.service.AuditLogService;
import com.example.honeycomb.util.HoneycombConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
                        .flatMap(event -> Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                                .subscribeOn(Schedulers.boundedElastic())
                                .onErrorReturn("{\"" + HoneycombConstants.JsonKeys.ERROR + "\":\""
                                        + HoneycombConstants.Examples.SERIALIZATION
                                        + "\"}")
                                .map(session::textMessage))
        );
    }
}
