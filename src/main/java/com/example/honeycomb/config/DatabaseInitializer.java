package com.example.honeycomb.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer {
    private final DatabaseClient client;

    public DatabaseInitializer(ConnectionFactory connectionFactory) {
        this.client = DatabaseClient.create(connectionFactory);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        client.sql("CREATE TABLE IF NOT EXISTS domain_addresses (id IDENTITY PRIMARY KEY, domain_name VARCHAR(255), host VARCHAR(255), port INT)")
                .fetch().rowsUpdated().subscribe();
    }
}
