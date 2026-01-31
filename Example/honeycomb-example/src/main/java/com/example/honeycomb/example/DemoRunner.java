package com.example.honeycomb.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class DemoRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoRunner.class);

    private final WebClient webClient;

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${honeycomb.security.api-keys.header:X-API-Key}")
    private String apiKeyHeader;

    @Value("${honeycomb.security.api-keys.keys.admin:admin-key}")
    private String adminKey;

    @Value("${shared.user:shared}")
    private String sharedUser;

    @Value("${shared.password:changeit}")
    private String sharedPassword;

    @Value("${shared.caller-header:X-From-Cell}")
    private String callerHeader;

    public DemoRunner(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    @Override
    public void run(ApplicationArguments args) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(2000);
                runDemo().block();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                log.warn("demo runner failed", ex);
            }
        });
    }

    private Mono<Void> runDemo() {
        String base = "http://localhost:" + serverPort;
        return listModels(base)
                .then(describeModel(base, "InventoryCell"))
                .then(createInventoryItem(base))
                .then(listInventory(base))
                .then(callSharedDirect(base))
                .then(callSharedViaCells(base))
                .then(fetchMetrics(base))
                .then(fetchAudit(base))
                .then();
    }

    private Mono<Void> listModels(String base) {
        return webClient.get().uri(base + "/honeycomb/models")
                .header(apiKeyHeader, adminKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .doOnNext(body -> log.info("models: {}", body))
                .then();
    }

    private Mono<Void> describeModel(String base, String name) {
        return webClient.get().uri(base + "/honeycomb/models/" + name)
                .header(apiKeyHeader, adminKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .doOnNext(body -> log.info("describe {}: {}", name, body))
                .then();
    }

    private Mono<Void> createInventoryItem(String base) {
        Map<String, Object> body = Map.of(
                "id", "inv-100",
                "sku", "SKU-RED-1",
                "quantity", 12,
                "warehouse", "west-1"
        );
        return webClient.post().uri(base + "/honeycomb/models/InventoryCell/items")
                .header(apiKeyHeader, adminKey)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .doOnNext(resp -> log.info("create inventory: {}", resp))
                .then();
    }

    private Mono<Void> listInventory(String base) {
        return webClient.get().uri(base + "/honeycomb/models/InventoryCell/items")
                .header(apiKeyHeader, adminKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .doOnNext(resp -> log.info("inventory items: {}", resp))
                .then();
    }

    private Mono<Void> callSharedDirect(String base) {
        Map<String, Object> body = Map.of(
                "listPrice", 49.99,
                "discountPct", 0.15
        );
        return webClient.post().uri(base + "/honeycomb/shared/discount")
                .headers(h -> {
                    h.setBasicAuth(sharedUser, sharedPassword);
                    h.add(callerHeader, "demo-client");
                })
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .doOnNext(resp -> log.info("shared discount: {}", resp))
                .then();
    }

    private Mono<Void> callSharedViaCells(String base) {
        Map<String, Object> body = Map.of(
                "listPrice", 49.99,
                "discountPct", 0.10
        );
        return webClient.post().uri(base + "/cells/demo-client/invoke/PricingCell/shared/discount?policy=round-robin")
                .headers(h -> {
                    h.setBasicAuth(sharedUser, sharedPassword);
                    h.add(callerHeader, "demo-client");
                })
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .doOnNext(resp -> log.info("cells invoke discount: {}", resp))
                .then();
    }

    private Mono<Void> fetchMetrics(String base) {
        return webClient.get().uri(base + "/honeycomb/metrics/cells")
                .header(apiKeyHeader, adminKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .doOnNext(resp -> log.info("metrics: {}", resp))
                .then();
    }

    private Mono<Void> fetchAudit(String base) {
        return webClient.get().uri(base + "/honeycomb/audit")
                .header(apiKeyHeader, adminKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .doOnNext(resp -> log.info("audit: {}", resp))
                .then();
    }
}
