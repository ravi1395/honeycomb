package com.example.honeycomb.config;

import com.example.honeycomb.web.EventWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.HashMap;

@Configuration
public class WebSocketConfig {
    @Bean
    public HandlerMapping webSocketMapping(EventWebSocketHandler handler) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        var urls = new HashMap<String, Object>();
        urls.put("/honeycomb/ws/events", handler);
        mapping.setUrlMap(urls);
        mapping.setOrder(-1);
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
